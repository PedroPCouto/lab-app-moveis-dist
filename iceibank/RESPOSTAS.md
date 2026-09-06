# ICEIBank — Sprint 1 — RESPOSTAS

Projeto: ICEIBank, Sprint 1 (U2 — Desenvolvimento Web / REST-MVC + relógio lógico de Lamport).
Linguagem escolhida para os 4 sprints: **Java (Spring Boot)**.

Todas as saídas citadas aqui foram tiradas da execução real registrada em
`evidencias/sprint1/` (os `.png` e as saídas de texto correspondentes em
`evidencias/sprint1/saidas/`).

---

## Parte B — Relógio de Lamport (seção 6.4)

### 1. Por que `max(contador_local, timestampRecebido) + 1` ao receber, em vez de adotar o timestamp recebido?

Porque adotar o timestamp recebido direto **quebraria a única garantia que o relógio de
Lamport oferece**: se A aconteceu antes de B, então `timestamp(A) < timestamp(B)`.

São dois problemas distintos, e o `max` resolve um enquanto o `+ 1` resolve o outro:

- **O `max` impede o relógio de andar para trás.** Se a Agência 0 já processou 10 eventos
  locais e simplesmente adotasse um timestamp 3 recebido de uma agência mais lenta, o
  próximo evento local dela sairia com timestamp 4 — menor que os 10 eventos que ela mesma
  já registrou e que, obviamente, aconteceram antes. A ordem causal *dentro do próprio
  processo* seria violada, que é o caso mais básico de "aconteceu antes".
- **O `+ 1` garante que o recebimento é estritamente posterior ao envio.** Sem ele, o evento
  de envio e o de recebimento ficariam com o mesmo número, e eles têm relação causal
  inegável: a mensagem não pode chegar antes de sair. Empate significaria "não sei ordenar",
  o que seria falso aqui.

Na evidência `transferencia-entre-agencias.png` isso aparece literalmente: a Agência 0 envia
com timestamp 9 e a Agência 1, que estava em 4, registra o crédito remoto com
`"timestampLamport": 10, "timestampRecebido": 9` — ou seja, `max(4, 9) + 1 = 10`.

### 2. Agência 0 está em 10 e recebe uma mensagem com timestamp 3. Qual o novo valor? O que isso implica?

**O novo contador é 11**: `max(10, 3) + 1 = 11`. O timestamp 3 é simplesmente ignorado, porque
o relógio local já está bem à frente dele.

O que isso implica sobre agências rápidas versus lentas:

- **O relógio nunca retrocede e nunca é "puxado para baixo" por um processo atrasado.** Uma
  mensagem de uma agência lenta não consegue arrastar uma agência movimentada de volta.
- **A velocidade fica assimétrica.** Uma agência que processa muitos eventos sobe o contador
  sozinha, no seu próprio ritmo. Uma agência parada só sobe quando faz algo ou quando recebe
  uma mensagem — e, ao receber, dá um salto para acompanhar quem está na frente. A Agência 1
  no teste pulou de 4 para 10 em um único evento: ela "envelheceu" 6 unidades de uma vez só
  porque conversou com uma agência mais adiantada.
- **Isso deixa claro que o contador não mede tempo nem carga de trabalho.** Um contador em 10
  não significa "10 eventos aconteceram aqui" — parte pode ter vindo de saltos por
  sincronização. Ele mede *posição em uma ordem causal*, não quantidade nem duração.
- **A consequência prática é que timestamps de agências diferentes não são comparáveis como
  medida.** Só a relação `<` entre eventos causalmente ligados tem significado; a diferença
  numérica entre eles não quer dizer nada.

O teste `RelogioLamportTest.aoReceberNuncaRetrocedeOContador` cobre exatamente esse cenário.

---

## Parte D — Transferências (seção 8.3)

### 1. Por que a transferência local não precisa de `aoEnviar()`/`aoReceber()` e a transferência entre agências precisa?

Porque **as regras 2 e 3 do algoritmo existem para sincronizar relógios de processos
diferentes**, e na transferência local só existe um processo envolvido.

Na transferência dentro da mesma agência, débito e crédito acontecem na mesma JVM, sobre o
mesmo objeto `RelogioLamport`. Não há mensagem trocada, não há dois contadores para
reconciliar: são apenas dois eventos locais consecutivos, e o incremento simples (`regra 1`)
já garante que o débito tem timestamp menor que o crédito. É o que se vê em
`transferencia-local.png`:

```
TRANSFERENCIA_DEBITO   timestampLamport: 6
TRANSFERENCIA_CREDITO  timestampLamport: 7
```

Já na transferência entre agências existem dois contadores independentes que nunca se viram.
A Agência 1 não tem como saber que a Agência 0 está em 8 — a única informação que ela recebe
é o timestamp que vem carimbado na requisição HTTP. Por isso:

- a Agência 0 usa `aoEnviar()` (regra 2) para incrementar e **anexar** o valor ao corpo da
  chamada `/creditar-remoto`;
- a Agência 1 usa `aoReceber(t)` (regra 3) para ajustar o contador dela para `max(local, t) + 1`.

Sem esse par de regras, os dois contadores evoluiriam de forma totalmente descolada e a linha
do tempo unificada da Parte E perderia o sentido: um crédito remoto poderia aparecer *antes*
do débito que o originou.

### 2. O saldo da origem foi revertido depois do erro? O que isso significa para a consistência?

**Não foi revertido.** Em `falha-conhecida.png`, com a Agência 1 derrubada:

| momento | saldo da conta 0 |
|---|---|
| antes da transferência | R$ 53,00 |
| depois do erro HTTP 502 | R$ 43,00 |

Os R$ 10,00 saíram da conta de origem e não entraram em lugar nenhum. A conta de destino
nunca foi creditada, e o log da Agência 0 registrou o evento `TRANSFERENCIA_FALHOU` com
`"inconsistencia": "debito aplicado sem credito correspondente"`.

Em termos de consistência: o sistema **perdeu a atomicidade da operação**. A transferência
deveria ser uma unidade indivisível ("debita E credita, ou não faz nada"), e ela quebrou no
meio, deixando o sistema num estado que nenhuma sequência válida de operações poderia ter
produzido. A invariante global que um banco precisa manter — *a soma dos saldos de todas as
agências só muda por depósito ou saque* — foi violada: R$ 10,00 sumiram do total sem que
ninguém tenha sacado.

Vale notar o que **não** está errado aqui: cada agência, isoladamente, está perfeitamente
consistente. A Agência 0 debitou uma conta com saldo suficiente e registrou tudo. O problema
só existe quando se olha o sistema como um todo — que é precisamente a dificuldade que
caracteriza um sistema distribuído. Isso é intencional neste sprint, e está registrado de
forma explícita — no evento `TRANSFERENCIA_FALHOU` gravado por
`TransferenciaService.creditarEmOutraAgencia`, com o campo `inconsistencia`, e na própria
resposta HTTP 502 — em vez de escondido.

### 3. Duas formas de corrigir isso no Sprint 4 (em alto nível)

**a) Two-Phase Commit (2PC) — consenso antes de efetivar.**
Um coordenador (a agência de origem) roda a operação em duas fases. Na fase de preparação,
ele pergunta às duas agências se elas *conseguem* fazer sua parte; cada uma reserva os
recursos (a origem bloqueia os R$ 10,00, sem debitar de fato) e responde "sim" ou "não". Só
se todas responderem "sim" o coordenador manda efetivar na segunda fase; qualquer "não" ou
silêncio vira um abort global e as reservas são liberadas. Com isso, o débito nunca chega a
existir sozinho. O custo é que o 2PC é **bloqueante**: se o coordenador cair entre as fases,
os participantes ficam com recursos travados esperando uma decisão que não vem — e o sistema
inteiro trava junto.

**b) Saga — compensação depois de efetivar.**
Aceita-se que a operação seja quebrada em passos locais que se efetivam de imediato (débito
na origem, depois crédito no destino), mas cada passo ganha uma **transação de compensação**
que desfaz o efeito dele. Se o crédito no destino falhar, a saga dispara automaticamente o
estorno do débito na origem. Não há consistência instantânea — existe uma janela em que o
dinheiro realmente está "no ar" —, mas o sistema converge para um estado consistente sozinho
(*consistência eventual*), e nunca fica bloqueado. É a abordagem que combina com a
mensageria do Sprint 2: a compensação vira um evento publicado, não uma chamada síncrona.

O trade-off resume-se a: **2PC troca disponibilidade por consistência imediata; Saga troca
consistência imediata por disponibilidade.**

---

## Parte E — Linha do tempo unificada (seções 10.2 e 10.3)

### Observação do passo 3 da tarefa (par de eventos empatados)

A execução gerou 29 eventos e **18 deles ficaram empatados com eventos de outras agências**
(`linha-do-tempo.png`). O par mais claro está logo no começo:

```
[Lamport 1] (2026-09-06T13:43:42.537836950Z) agencia-0 - CRIAR_CONTA {"id":0,...}   <-- EMPATE
[Lamport 1] (2026-09-06T13:43:42.614716334Z) agencia-1 - CRIAR_CONTA {"id":1,...}   <-- EMPATE
[Lamport 1] (2026-09-06T13:43:42.677533369Z) agencia-2 - CRIAR_CONTA {"id":2,...}   <-- EMPATE
```

**Eles são causalmente relacionados ou concorrentes?** São **concorrentes**. Criar a conta 0
na Agência 0 não influenciou em nada a criação da conta 1 na Agência 1 — as três agências
nem haviam trocado mensagem ainda naquele momento, cada uma estava simplesmente no primeiro
evento do seu próprio contador. O empate aqui não é coincidência: é a consequência direta de
três processos independentes começarem em zero e evoluírem sozinhos.

**A ordem por hora de parede bate com a ordem por Lamport?** Não necessariamente — e a
execução produziu um caso de inversão explícita:

```
[Lamport 2] (2026-09-06T13:43:42.715620740Z) agencia-2 - DEPOSITO {"id":2,...}
[Lamport 3] (2026-09-06T13:43:42.715088401Z) agencia-0 - DEPOSITO {"id":0,...}
```

Ordenados por Lamport, o evento da Agência 2 vem antes (2 < 3). Pela hora de parede, ele
aconteceu **depois** (`.715620` > `.715088`, meio milissegundo mais tarde). As duas ordens se
contradizem.

Isso não é um defeito do relógio de Lamport: ele nunca prometeu concordar com o relógio
físico. Esses dois eventos são concorrentes, então *qualquer* ordem entre eles é igualmente
válida do ponto de vista causal — inclusive a que o relógio físico sugere, e inclusive a
oposta. O que seria um defeito de verdade é o contrário: dois eventos causalmente ligados
saindo fora de ordem por Lamport — e isso nunca acontece, como se vê no par
envio (Agência 0, `t=9`) → recebimento (Agência 1, `t=10`).

Um detalhe reforça o argumento: os relógios de parede aqui são todos da **mesma máquina**,
perfeitamente sincronizados entre si. Em três máquinas diferentes, com desvio de relógio
(*clock skew*) de dezenas de milissegundos ou mais, a hora de parede seria ainda menos
confiável para ordenar eventos — que é justamente o motivo de o Lamport existir.

### 1. Lamport garante uma direção, não a volta. O que isso significa na prática?

Significa que, olhando dois eventos A e B com `timestamp(A) < timestamp(B)`, **não dá para
concluir nada**. Existem duas explicações possíveis para esse mesmo dado e o relógio não
distingue entre elas:

1. A realmente aconteceu antes de B e influenciou B (relação causal);
2. A e B são concorrentes, não têm nenhuma relação, e o número menor de A é acidental —
   consequência de qual processo processou mais eventos antes, não de quem veio primeiro.

Na prática, a linha do tempo unificada é **uma ordem total válida, mas não é *a* ordem dos
acontecimentos**. Ela é uma linearização possível de uma realidade que é parcialmente
ordenada. Serve para depurar, para ter um "número de sequência" consistente e para garantir
que causas sempre aparecem antes dos efeitos. Não serve para afirmar que um evento causou
outro, nem para resolver conflitos ("quem escreveu por último ganha") — usar Lamport para
isso descartaria silenciosamente uma das duas escritas concorrentes, sem nem perceber que
houve conflito.

A única conclusão realmente segura é a **contrapositiva**: se `timestamp(A) >= timestamp(B)`,
então A definitivamente **não** causou B.

### 2. Lamport sozinho basta para distinguir "concorrentes" de "aconteceu antes"? Por que isso motiva o relógio vetorial?

**Não basta.** O caso dos 18 empates é a demonstração: o relógio devolve um único inteiro por
evento, e um inteiro só sabe dizer "menor, igual ou maior". Ele não tem como codificar a
diferença entre "B depende de A" e "A e B não se conhecem".

O empate é a única pista de concorrência que Lamport dá — e é uma pista fraca e incompleta:
eventos concorrentes muitas vezes recebem timestamps *diferentes*, como o par
`[Lamport 2] agencia-2` / `[Lamport 3] agencia-0` acima, que é concorrente apesar de não
empatar. Ou seja: empate sugere concorrência, mas a ausência de empate não prova causalidade.

A causa raiz do problema é a **perda de informação**: ao comprimir toda a história de um
evento em um único número, o algoritmo joga fora *de quem* veio cada incremento.

É exatamente isso que o **relógio vetorial** do Sprint 2 recupera. Em vez de um inteiro, cada
processo carrega um vetor com uma posição por processo — no ICEIBank, `[c0, c1, c2]`, uma para
cada agência. Cada agência incrementa apenas a própria posição e, ao receber uma mensagem,
faz o máximo posição a posição. Com isso a comparação passa a ter **três** resultados em vez
de dois:

- `V(A) < V(B)` em todas as posições → A aconteceu antes de B, com certeza;
- `V(B) < V(A)` → B aconteceu antes de A;
- **nenhum dos dois domina o outro** (cada um é maior em alguma posição) → A e B são
  **provadamente concorrentes**.

Esse terceiro caso é o que Lamport não consegue expressar, e é o que transforma "provavelmente
concorrentes" em "concorrentes, com certeza". O preço é o tamanho: o carimbo cresce com o
número de processos, enquanto o de Lamport é sempre um inteiro só.

---

## Parte F — Autenticação JWT (seção 11.3)

### Decisões de projeto (justificativas pedidas na seção 11.1)

**Formato das credenciais: usuário + senha (requisito 1).**
Descartei "id de conta + senha" porque a partição de contas torna esse modelo errado neste
domínio: a mesma pessoa pode ter contas em agências diferentes (a Ana tem a conta 0 e a conta
3 na Agência 0, e nada a impede de abrir a conta 4 na Agência 1). Amarrar o login a uma conta
significaria uma senha por conta e um login novo a cada troca de agência — e, pior, deixaria
de existir um conceito de *pessoa* no sistema, que é justamente o que permite responder
"quem é o dono desta conta". Com usuário + senha, a identidade é independente da partição, e
o mesmo token funciona nas 3 agências (todas assinam com a mesma chave). Isso já prepara o
terreno para a funcionalidade de extrato consolidado entre agências, se ela vier num sprint
futuro.

A base de usuários é fixa e vem do `application.yaml` (não há banco neste sprint, por decisão
do próprio roteiro). As senhas ficam como **hash SHA-256**, nunca em texto puro, e a
comparação usa `MessageDigest.isEqual`, que é resistente a ataque de tempo. Limitação
conhecida e assumida: SHA-256 é rápido demais para senha — o correto em produção seria bcrypt
ou Argon2, que são lentos de propósito para encarecer ataque de força bruta. Não usei aqui
para não trazer o Spring Security inteiro só por causa do `PasswordEncoder`.

O token expira em **30 minutos** (requisito 2). É curto o bastante para limitar o estrago de
um token vazado e longo o bastante para não atrapalhar uma sessão de teste.

**A chamada entre agências carrega token? Sim — mas um token de outro tipo (requisito 5).**

Considerei três caminhos:

1. *Deixar `/creditar-remoto` aberta.* Descartado: é a rota mais perigosa da API — ela credita
   dinheiro sem exigir contrapartida. Deixá-la sem autenticação significaria que qualquer um
   com acesso à rede poderia creditar qualquer conta à vontade, e não adiantaria nada proteger
   as outras rotas.
2. *Repassar o token da pessoa que iniciou a transferência.* Descartado por dois motivos.
   Primeiro, semanticamente errado: quem credita a conta de destino não é a Ana, é a Agência 0
   agindo como sistema — a Ana não tem (nem deve ter) permissão sobre a conta do Bruno.
   Segundo, é um problema de segurança conhecido (*confused deputy*): repassar credencial de
   usuário para serviço interno faz a autorização vazar de um contexto para o outro.
3. **Escolhido: um token próprio, de tipo `SISTEMA`, emitido pela agência de origem.** Ele é
   assinado com a mesma chave HMAC (por isso a chave precisa ser idêntica nas 3 agências),
   traz `sub = "agencia-0"`, `tipo = "SISTEMA"`, e vale **60 segundos** — tempo de uma chamada,
   não de uma sessão.

A separação é aplicada nos dois sentidos, no `JwtFilter`:

- um token `CLIENTE` em `/creditar-remoto` → **403**;
- um token `SISTEMA` em qualquer rota de conta → **403**.

Ou seja, nem a pessoa credita direto, nem a agência saca de conta alheia. Os dois casos estão
cobertos por teste (`ApiIntegracaoTest.tokenDeClienteNaoAcessaRotaInterna`) e aparecem em
`auth-token-expirado.png`.

A vantagem prática de reaproveitar o JWT em vez de inventar um "segredo interno" em header
separado é que ganho expiração e identidade de graça: o log do crédito remoto registra
`"chamadoPor": "agencia-0"`, e um token interno interceptado vira inútil em um minuto.

### 1. Diferença entre autenticação e autorização. Sua implementação verifica as duas?

**Autenticação** responde "quem é você?" e é resolvida por prova de identidade — aqui, a
senha no login e depois a assinatura do JWT em cada requisição. **Autorização** responde "o
que você pode fazer?" e só faz sentido depois que a identidade está estabelecida.

Confundir as duas é a origem do bug clássico: uma API que checa o token e, satisfeita por ele
ser válido, executa qualquer coisa que ele pedir.

**A implementação verifica as duas**, em camadas diferentes de propósito:

| camada | responsabilidade | onde |
|---|---|---|
| `JwtFilter` | autenticação (assinatura, expiração) + autorização grossa por tipo de token | `security/JwtFilter.java` |
| `ContaService` | autorização fina: só o dono opera a conta | `service/ContaService.java` |

Respondendo à pergunta direta do enunciado — **um usuário autenticado consegue sacar de uma
conta que não é dele?** **Não.** Cada conta guarda o campo `dono`, preenchido no momento da
criação com o `sub` do token de quem a criou, e `buscarDoUsuario` compara o dono com o usuário
autenticado antes de qualquer operação de leitura ou escrita, devolvendo **403** quando não
bate:

```
$ curl .../contas/1 -H "Authorization: Bearer <token da ana>"
{"erro":"Conta 1 nao pertence ao usuario autenticado."}
```

O teste `ContaServiceTest.usuarioAutenticadoNaoOperaContaDeOutro` cobre esse caso: o Bruno
está corretamente **autenticado** e mesmo assim é barrado por **não estar autorizado**.

A distinção fica ainda mais visível no código de status escolhido: token ausente/inválido/
expirado → **401** ("não sei quem você é"); token válido operando conta alheia → **403** ("sei
quem você é, e você não pode").

### 2. Por que o servidor não precisa consultar o banco para validar a assinatura? O que isso implica sobre escalabilidade?

O JWT é **autocontido e assinado**. Ele carrega os dados (`sub`, `tipo`, `exp`) no próprio
payload, e a assinatura HMAC-SHA é calculada sobre esses dados com uma chave secreta. Para
validar, o servidor recomputa o HMAC do header+payload recebidos com a chave que ele já tem
em memória e compara com a assinatura que veio junto. Se bater, os dados não foram alterados,
porque produzir uma assinatura válida sem a chave é computacionalmente inviável. A expiração
também vem dentro do token, então a verificação é uma comparação de data. **Nada disso exige
estado do lado do servidor** — é aritmética sobre bytes que o próprio cliente trouxe.

Comparado a sessões em memória, a diferença de escalabilidade é estrutural:

- **Sessão em memória**: o servidor guarda `id_de_sessao → usuário`. Com várias instâncias,
  cada requisição precisa cair na instância que tem aquela sessão (*sticky sessions*), ou a
  sessão precisa ser movida para um armazenamento compartilhado (Redis), que vira uma ida à
  rede por requisição e um novo ponto único de falha. O estado das sessões cresce com o número
  de usuários logados e some se o processo reiniciar.
- **JWT**: qualquer instância valida qualquer token, porque a única coisa compartilhada é a
  chave — que é estática e cabe na configuração. As instâncias ficam *stateless* e podem ser
  criadas, destruídas e balanceadas livremente.

Isso é exatamente o que acontece no ICEIBank: as 3 agências são processos separados e o token
emitido pela Agência 0 é aceito pela Agência 1 sem nenhuma comunicação entre elas. No frontend
dá para trocar a "porta de entrada" de agência sem refazer o login.

O preço dessa liberdade é a **revogação**. Como não existe registro central, não há como
invalidar um token específico antes de ele expirar — trocar a senha, por exemplo, não derruba
os tokens já emitidos. É por isso que a expiração curta importa tanto: ela é o único mecanismo
de revogação que sobra. Um sistema real resolveria isso com token de acesso curto + *refresh
token* revogável, ou com uma lista de revogação — que reintroduz estado, e portanto parte do
custo que o JWT tinha eliminado.

### 3. O que aconteceria se a chave secreta vazasse?

**Colapso total da autenticação do sistema.** Quem tem a chave pode *forjar* tokens, não
apenas ler os existentes. Concretamente:

- **Personificação de qualquer usuário.** Basta assinar um token com `sub` de outra pessoa
  para operar todas as contas dela — consultar, sacar, transferir. O sistema não teria como
  distinguir esse token de um legítimo: ele *é* legítimo do ponto de vista criptográfico.
- **Personificação das próprias agências.** Como escolhi assinar o token interno com a mesma
  chave, o atacante também consegue forjar um token `tipo: SISTEMA` e chamar `/creditar-remoto`
  diretamente, criando dinheiro do nada sem nenhum débito correspondente.
- **Expiração deixa de proteger.** Ela só vale contra tokens roubados; com a chave em mãos,
  emite-se um token novo a qualquer momento.
- **O vazamento é silencioso.** Nenhum log registra "chave usada indevidamente", porque do
  ponto de vista do servidor não há nada de anormal. Pode passar despercebido indefinidamente.
- **A única correção é rotacionar a chave** — o que invalida de uma vez todos os tokens em
  circulação e desloga todo mundo. Como HS256/HS384 é simétrico, a rotação precisa ser
  coordenada entre as 3 agências: enquanto uma tiver a chave antiga e outra a nova, as
  transferências entre agências param.

É por isso que a chave está parametrizada como `${JWT_SEGREDO:...}` no `application.yaml`: em
produção ela viria de variável de ambiente ou cofre de segredos, **nunca versionada no
repositório**. O valor que está no arquivo é explicitamente rotulado como chave de
laboratório. Mitigações razoáveis num sistema real seriam: chave só em cofre com auditoria de
acesso, rotação periódica programada (com janela de aceitação das duas chaves para não
derrubar as chamadas entre agências), e migração para assinatura **assimétrica** (RS256/ES256)
— aí cada agência valida com a chave pública e só o emissor guarda a privada, reduzindo de 3
para 1 o número de lugares de onde a chave de assinatura pode vazar.

---

## Parte G — Frontend (seção 12.3)

### Decisões de projeto

**Tecnologia: HTML + CSS + JavaScript puro, sem framework e sem build.** O requisito é ser
web e funcional, e um framework acrescentaria `node_modules`, um passo de build e um servidor
de desenvolvimento sem melhorar nada do que o roteiro pede. Além disso, o item 3 da seção
12.3 pergunta onde estão o M, o V e o C — sem framework, essa separação é uma escolha
explícita minha, e não um efeito colateral da ferramenta. A página abre direto pelo
`index.html` (o backend libera CORS) ou via `scripts/servir-frontend.sh`.

**Guarda do token: `localStorage`.** Escolhido em vez de `sessionStorage` para a sessão
sobreviver ao recarregar a página, e em vez de cookie porque cookie enviado automaticamente
abriria a porta para CSRF — o cabeçalho `Authorization` precisa ser posto de propósito pelo
código, o que já elimina essa classe de ataque. A limitação assumida é que `localStorage` é
legível por JavaScript, então um XSS na página rouba o token; num sistema real a alternativa
seria cookie `HttpOnly` + `SameSite` com proteção anti-CSRF.

### 1. Como o frontend "lembra" de reenviar o token em cada requisição?

O token nunca é reenviado à mão. Existe **um único ponto de saída HTTP** em todo o frontend —
a função `requisitar()` em `js/modelo.js` — e é ela que anexa o cabeçalho:

```js
if (estado.token) {
  opcoes.headers['Authorization'] = 'Bearer ' + estado.token;
}
```

Todas as operações (`minhasContas`, `depositar`, `sacar`, `transferir`, `historico`, ...) são
casos particulares dessa função. O controlador chama `Modelo.transferir(...)` e não sabe que
existe um cabeçalho `Authorization` — é impossível esquecer o token em uma chamada nova,
porque não há como fazer uma chamada por fora.

O ciclo completo é:

1. `entrar()` faz o `POST /auth/login`, recebe o token e guarda em `estado.token` e no
   `localStorage` (`guardarSessao`);
2. cada chamada seguinte lê `estado.token` e monta o cabeçalho;
3. ao recarregar a página, `restaurarSessao()` lê o `localStorage` de volta para `estado` — se
   houver token, o controlador já abre direto no painel, sem passar pelo login;
4. `sair()` limpa as duas cópias.

É o mesmo papel de um interceptor do Axios ou do `HttpClient` do Angular, só que escrito na mão.

### 2. Se o token expirar no meio de uma operação, o que acontece?

**A interface avisa explicitamente**, e não com um erro genérico. A `requisitar()` converte
toda resposta de erro em um `ErroApi` que carrega o status HTTP, e o controlador tem um
tratador central (`tratarErro`) que dá tratamento especial ao 401:

```js
if (erro instanceof Modelo.ErroApi && erro.status === 401) {
  Modelo.sair();
  Visao.mostrarLogin(...);
  Visao.mostrarMensagem('alerta', 'Sua sessao expirou ou o token nao e mais valido. Entre novamente. ...');
}
```

Na prática a pessoa vê três coisas ao mesmo tempo: o aviso em destaque no topo da tela, o
retorno automático à tela de login e a limpeza do token inválido do `localStorage` — que
evita o loop de continuar tentando com uma credencial morta.

Os demais erros (saldo insuficiente, conta não encontrada, 502 de agência fora do ar) caem no
caso geral e aparecem na mesma faixa de mensagem, com a mensagem que o **backend** escreveu —
não uma mensagem inventada pelo frontend. As evidências `frontend-erro.png` ("Saldo
insuficiente.") e `frontend-falha-entre-agencias.png` ("Falha ao contatar agencia de destino.
Debito ja aplicado — inconsistencia conhecida (ver Sprint 4).") mostram os dois casos.

Uma limitação honesta: a expiração só é detectada **quando uma requisição é feita**. Se o
token vencer com a tela parada, a pessoa só descobre na ação seguinte. Um temporizador
comparando com `expiraEmSegundos` (que o login já devolve) resolveria isso, e ficou como
melhoria possível.

### 3. Onde ficam o M, o V e o C no seu frontend?

A separação é explícita, um arquivo por papel, carregados nessa ordem no `index.html`:

| camada | arquivo | responsabilidade | o que ele **não** faz |
|---|---|---|---|
| **Model** | `js/modelo.js` | estado da sessão, cliente HTTP, regra de partição (`agenciaResponsavel`) | não tem um único `document.querySelector` |
| **View** | `js/visao.js` | monta tabelas, pílulas de status, mensagens, alterna telas | não faz `fetch`, não decide regra |
| **Controller** | `js/controlador.js` | escuta eventos do DOM, chama o Modelo, manda a Visão redesenhar, trata erro | não monta HTML nem monta requisição |

O `index.html` é a View estática (estrutura e formulários) e o `css/estilo.css` é só
apresentação.

**Ficou claro ou misturado?** Ficou razoavelmente limpo, e a prova é que Modelo e Visão não se
conhecem: o `modelo.js` roda inteiro sem DOM, e o `visao.js` recebe dados já prontos. O
controlador é o único que importa os dois. Dois pontos, porém, merecem honestidade:

- **O Modelo duplica uma regra do backend.** `agenciaResponsavel(id)` reimplementa o `% 3` do
  servidor, só para dar a dica "conta 5 pertence à agência 2" antes de enviar a requisição. É
  duplicação consciente: melhora muito a usabilidade, e o backend continua sendo a autoridade
  — se as duas discordarem, quem manda é o servidor, que devolve 400.
- **A Visão tem um resquício de estilo inline** em alguns botões gerados por JavaScript, que
  idealmente deveria estar no CSS. É o ponto mais "misturado" do conjunto.

O que **não** aconteceu foi o antipadrão mais comum em frontend sem framework: manipular DOM
dentro do callback do `fetch`. Cada `then` do controlador chama uma função nomeada da Visão,
nunca mexe no DOM diretamente.

---

## Funcionalidade adicional (seção 2.1)

Implementei **duas rotas de observabilidade** que não existem no roteiro: histórico de eventos
por conta e health-check por agência.

### `GET /contas/{id}/historico?limite=N` — histórico de transações da conta

Devolve os últimos eventos registrados que envolvem aquela conta, do mais recente para o mais
antigo, com o timestamp de Lamport de cada um. O `RegistroEventos` passou a manter os eventos
também em memória (além de gravá-los no `.jsonl`), e o filtro procura o id da conta nas chaves
`id`, `idConta`, `idOrigem` e `idDestino` dos detalhes — assim um extrato traz tanto o que a
conta recebeu quanto o que ela enviou. A rota exige token **e** é sujeita à mesma regra de
autorização das demais: só o dono vê o histórico da própria conta.

### `GET /status` — health-check por agência

Rota **pública** (é a única, junto com o login) que devolve a identidade da agência, a porta,
**o valor atual do relógio de Lamport**, a quantidade de contas sob responsabilidade daquela
partição e a lista de ids. A leitura *não* incrementa o contador: consultar o status não é um
evento do sistema distribuído, e fazê-lo contar poluiria a linha do tempo com eventos que não
representam nada.

### Por que escolhi estas

- **O histórico transforma o log em algo utilizável.** Até então, ver o que aconteceu com uma
  conta exigia abrir o `.jsonl` na mão. Como o `RegistroEventos` já registrava tudo com
  carimbo de Lamport, o histórico expõe pela API exatamente o material da Parte E, agora
  filtrado por conta — o conceito central do sprint deixa de ser algo visível só em arquivo.
- **O `/status` era a peça que faltava para o frontend.** Sem ele, o frontend não teria como
  saber quais agências estão no ar, e a evidência `frontend-falha-entre-agencias.png` (que
  mostra "Agencia 1 — fora do ar" na tela enquanto as outras duas seguem funcionando) não
  existiria. Ele também deixa o relógio de Lamport de cada agência visível ao vivo, o que
  ajudou a entender o algoritmo durante o desenvolvimento muito mais do que ler o log depois.
- **As duas atendem ao critério de "comportamento novo e observável"**: são endpoints novos,
  com regra de autorização própria (uma protegida, outra deliberadamente pública), e não
  refatoração nem ajuste cosmético.

Evidência: `evidencias/sprint1/funcionalidade-adicional.png` (as duas rotas via API, incluindo
a tentativa do Bruno de ler o histórico da conta da Ana, barrada com 403) e
`evidencias/sprint1/frontend-historico.png` (o histórico renderizado na interface).

---

## Observações finais

**O que este sprint deliberadamente não resolve** (e está documentado, não escondido):

- A transferência entre agências não é atômica sob falha — Parte D, questão 2. Assunto do
  Sprint 4.
- As contas vivem em memória; reiniciar uma agência apaga as contas dela (o log de eventos,
  esse sim, sobrevive em disco). É o que o roteiro pede na seção 7.2.
- Não há cadastro de usuários: a base é fixa no `application.yaml`.
- Não há revogação de token antes da expiração — Parte F, questão 2.

**Testes automatizados**: 29 testes cobrindo as três regras do relógio de Lamport (incluindo o
cenário exato da pergunta 6.4.2 e um teste de concorrência com 8 threads), a regra de
partição, as regras de conta, a autorização por dono e os três cenários de token da Parte F.
Rodar com `cd agencia && ./mvnw test`.
