# ICEIBank — RESPOSTAS (Sprints 1 e 2)

Linguagem escolhida para os 4 sprints: **Java (Spring Boot)**.

- **Sprint 1** (U2 — REST/MVC + relógio de Lamport + JWT): seções logo abaixo.
- **Sprint 2** (U3 — mensageria RabbitMQ + relógio vetorial): [ao final do arquivo](#sprint-2--mensageria-pubsub-e-relógio-vetorial).

---

# Sprint 1

Projeto: ICEIBank, Sprint 1 (U2 — Desenvolvimento Web / REST-MVC + relógio lógico de Lamport).

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

---

# Sprint 2 — Mensageria (Pub/Sub) e relógio vetorial

Projeto: ICEIBank, Sprint 2 (U3 — Comunicação indireta: RabbitMQ via CloudAMQP + relógio
vetorial). Mesma base Java/Spring Boot do Sprint 1; a integração com o RabbitMQ usa
**Spring AMQP** (`spring-boot-starter-amqp`), como sugere a seção 9.2 do roteiro.

Todas as saídas citadas aqui vêm da execução real de 04/10/2026 contra uma instância
CloudAMQP (Little Lemur), registrada em `evidencias/sprint2/` (os `.png`) e
`evidencias/sprint2/saidas/` (o texto de cada saída). A sequência rodada foi
`demo.sh preparar → concorrentes → local → entre-agencias → auth → resiliencia → mortas →
linha-do-tempo`, com os logs limpos no início.

## Como a arquitetura ficou

| Peça | Onde | O que faz |
|---|---|---|
| Exchange `iceibank.eventos` (topic, durável) | `config/MensageriaConfig` | para onde toda agência publica |
| Filas `fila-agencia-{0,1,2}` (duráveis) | idem | cada uma ligada por `agencia.<id>.creditar` |
| `RelogioVetorial` | `service/RelogioVetorial` | as três regras + `comparar(v1, v2)` |
| Publicação | `service/Mensageria.publicar` | mensagem persistente + **espera o publisher confirm** |
| Consumo | `service/ConsumidorCreditos` (`@RabbitListener`) | `aoReceber` + aplica o crédito |
| Linha do tempo causal | `tools/MesclarLogs` | lista pares concorrentes e confere os causais |

Três decisões que vão além do exemplo em Node e que precisei justificar para mim mesmo:

1. **Cada agência declara as três filas, não só a sua.** Uma exchange topic descarta em
   silêncio a mensagem que não casa com nenhuma fila. Se a agência 1 nunca tivesse subido,
   `fila-agencia-1` não existiria e a transferência para ela sumiria — justamente o caso
   que a mensageria deveria cobrir. Declarar é idempotente, então não custa nada.
2. **200 só depois do *publisher confirm*.** `channel.publish` sozinho só garante que os
   bytes saíram do processo. Com `publisher-confirm-type: simple` a transferência espera o
   broker confirmar que aceitou (e, para fila durável + mensagem persistente, gravou) a
   mensagem.
3. **Se a publicação falhar, o débito é estornado (503).** Diferente do Sprint 1, aqui é
   seguro desfazer: a mensagem não saiu da agência, ninguém mais viu esse dinheiro.

## Parte B — Relógio vetorial (seção 6.4)

### 1. Com 10 agências, o que acontece com o tamanho do vetor anexado a cada mensagem? Isso é um problema?

Cada vetor passaria de 3 para **10 posições**: o tamanho cresce linearmente com o número de
processos, O(N), em **toda** mensagem e em **todo** evento gravado no log.

Para o ICEIBank isso **não é um problema**, por dois motivos concretos:

- **O custo absoluto é irrisório.** Hoje o vetor ocupa `"vetorEnvio":[7,0,0]` dentro de uma
  mensagem JSON de ~120 bytes (com `idTransferencia`, valor etc.). Com 10 agências seriam uns
  20 caracteres a mais — menos que o próprio UUID da transferência.
- **O conjunto de processos é pequeno e fixo.** Os "processos" do relógio são as agências,
  não os clientes. O número de agências muda raramente e é conhecido de antemão
  (`AgenciaConfig.NUMERO_AGENCIAS`).

Vira problema quando N é grande **ou dinâmico**: milhares de nós, ou nós entrando e saindo
(cada um precisa de uma posição; um vetor de 1.000 inteiros em cada mensagem e em cada linha
de log já pesa, e é preciso combinar quem ocupa qual posição). Para esses casos existem
variações que comprimem o vetor ou só guardam as posições não nulas, como *version vectors*
por réplica, *dotted version vectors* e *interval tree clocks*. Mudar o número de agências
aqui também exigiria migrar os logs antigos, já que os vetores gravados têm 3 posições (o
`aoReceber` recusa vetores de tamanho diferente, coberto em `RelogioVetorialTest`).

### 2. `V1 = [3, 1, 0]` e `V2 = [3, 2, 0]`: qual aconteceu primeiro?

**V1 aconteceu antes de V2.** Posição a posição:

| posição | V1 | V2 | |
|---|---|---|---|
| 0 | 3 | 3 | igual |
| 1 | 1 | 2 | V1 < V2 |
| 2 | 0 | 0 | igual |

`V1[i] <= V2[i]` em todas as posições e os vetores são diferentes, logo V1 → V2. Lendo o
significado: o evento V2 "conhece" tudo o que V1 conhecia (3 eventos da agência 0, nenhum da
2) e mais um evento da agência 1. É o padrão de dois eventos consecutivos da agência 1 (ou de
um evento da agência 1 que recebeu, direta ou indiretamente, a história de V1).

### 3. `V1 = [3, 1, 0]` e `V2 = [1, 3, 0]`: qual aconteceu primeiro?

**São concorrentes.** Na posição 0, V1 > V2 (3 > 1); na posição 1, V1 < V2 (1 < 3). Nem
`V1 <= V2` nem `V2 <= V1`. V1 viu dois eventos da agência 0 que V2 não viu, e V2 viu dois
eventos da agência 1 que V1 não viu: nenhum dos dois pode ter influenciado o outro.

As duas comparações estão no teste `comparaRelacoesDasPerguntasDaParteB`, que chama o mesmo
`RelogioVetorial.comparar` usado pelo `MesclarLogs`.

### Observação: o relógio sobrevive ao reinício da agência

O exemplo do roteiro recria o relógio em `[0,0,0]` a cada `node src/app.js`. Isso quebra a
análise causal depois de um reinício: a agência 1 voltaria a emitir `[0,1,0]`, um vetor que
ela já tinha usado e que as outras agências já tinham visto. Eventos novos pareceriam
"anteriores" a coisas que aconteceram antes deles.

Por isso o `RelogioVetorial` lê o último vetor gravado no próprio `eventos-agencia-N.jsonl`
ao subir. Na evidência de resiliência isso aparece: a agência 1 caiu em `[7,3,0]` e, ao
voltar, seu primeiro evento foi `[9,4,0]` (`max([7,3,0],[9,0,0]) + 1` na posição 1), e não
`[9,1,0]`. As **contas** continuam em memória e se perdem (é o ponto da Parte C); o **log**
já era persistente desde o Sprint 1, e o relógio passou a acompanhá-lo.

## Parte C — Publish/Subscribe entre agências (seção 7.5)

Antes das perguntas, o que a transferência normal mostrou (`transferencia-assincrona.png`):

```
[agencia-0] [Vetor [6, 0, 0]] TRANSFERENCIA_DEBITO   ...
[agencia-0] [Vetor [7, 0, 0]] TRANSFERENCIA_ENVIADA  ... routingKey=agencia.1.creditar
[agencia-1] [Vetor [7, 3, 0]] TRANSFERENCIA_CREDITO_REMOTO ... vetorRecebido=[7, 0, 0]
```

A agência 1 estava em `[0,2,0]`. Ao receber `[7,0,0]`: `max` posição a posição = `[7,2,0]`,
mais 1 na própria posição = `[7,3,0]`. O crédito chegou 14 ms depois do envio, sem nenhuma
chamada HTTP entre as agências.

### 1. O que aconteceu quando a Agência 1 voltou? A mensagem "sumiu" por falha da mensageria?

Sequência observada (`resiliencia-fila.png`):

1. Agência 1 no ar com a conta 1 (saldo 115,00), vetor `[7,3,0]`.
2. Agência 1 derrubada (`/status` → HTTP 000).
3. Transferência de R$ 10,00 da conta 0 para a conta 1: **HTTP 200**, "Transferencia publicada
   para a agencia de destino (entrega assincrona)", vetor do envio `[9,0,0]`.
4. Pelo `/status` da agência 0: `"fila-agencia-1": 1`. A mensagem estava no broker,
   esperando.
5. Agência 1 religada. 23 ms depois de a aplicação terminar de subir (09:50:23.787), o log mostrou:
   ```
   [agencia-1] 09:50:23.810 [Vetor [9, 4, 0]] CREDITO_REMOTO_FALHOU
       {idTransferencia=614b73c3-..., idConta=1, valor=10, origemAgencia=0,
        vetorRecebido=[9, 0, 0], motivo=conta nao encontrada}
   ```
6. `/status` da agência 1: `"contas": []`, `"fila-agencia-1": 0`.
7. A conta 0 continuou debitada (saldo 120,00).

**A mensageria não falhou.** Ela fez exatamente o que prometia: guardou a mensagem enquanto
ninguém podia consumi-la e a entregou assim que a agência voltou. Prova disso é o próprio
log: o evento `CREDITO_REMOTO_FALHOU` existe, carrega o `idTransferencia` e o `vetorRecebido`
corretos, e a fila foi de 1 para 0. O crédito não foi aplicado **porque a conta 1 não existia
mais**: contas vivem em um `ConcurrentHashMap` em memória, e o processo novo da agência 1
começou vazio. A mensagem chegou intacta a um destinatário que tinha esquecido quem era.

No código do roteiro a história terminaria aí, com a mensagem confirmada (`ack`) e
descartada. Aqui ela foi para `fila-agencia-1.mortas` (`"fila-agencia-1.mortas": 1`), que é
a funcionalidade adicional, descrita mais abaixo.

### 2. Comparando com o Sprint 1: o que melhorou e o que continua em aberto?

**O que melhorou:**

- **Desacoplamento no tempo.** No Sprint 1, a agência de destino precisava estar no ar no
  exato momento da transferência; se não estivesse, a API devolvia 502 e pronto. Agora a
  origem não precisa saber se o destino está no ar, nem onde ele está: só conhece a exchange
  e a routing key.
- **A mensagem não se perde.** Exchange e fila duráveis, mensagem persistente e publisher
  confirm. Uma transferência feita com o destino fora do ar é entregue quando ele volta, sem
  nenhum reenvio manual. No Sprint 1 o crédito simplesmente nunca acontecia.
- **A falha que sobra do lado da origem ficou segura.** Se o RabbitMQ estiver indisponível,
  a publicação falha antes de qualquer outra agência ver o dinheiro, e o débito é estornado
  (`TRANSFERENCIA_ESTORNADA`, HTTP 503, testado em `falhaAoPublicarEstornaODebito`). No
  Sprint 1 o débito ficava pendurado.

**O que continua em aberto — "a mensagem não se perde" não é "o sistema está correto":**

- **Não há atomicidade entre débito e crédito.** O débito na agência 0 é aplicado na hora; o
  crédito na agência 1 acontece depois, e pode falhar de forma permanente (conta inexistente,
  como vimos). Nesse caso o dinheiro fica fora das duas contas e **ninguém avisa a origem**:
  a Ana recebeu 200, e 200 agora quer dizer apenas "o broker aceitou a mensagem". Falta uma
  compensação (estornar a origem quando o crédito falha) — é a Saga do Sprint 4.
- **Consistência eventual.** Mesmo no caminho feliz, existe uma janela em que o valor já saiu
  da conta 0 e ainda não entrou na conta 1. Quem somar os saldos nesse instante vê dinheiro
  sumido.
- **Entrega "pelo menos uma vez".** O `ack` é enviado depois que o método do consumidor
  termina. Se a agência cair *depois* de creditar e *antes* do `ack`, o RabbitMQ entrega a
  mensagem de novo e o crédito é aplicado duas vezes. O `idTransferencia` existe em toda
  mensagem, mas o consumidor ainda não o usa para descartar duplicatas (idempotência).
- **Estado em memória.** Enquanto as contas não forem persistidas, todo reinício é uma perda
  de dados — a mensageria só torna essa perda visível.

### 3. O consumidor processa créditos sem verificar JWT. Isso é um problema de segurança?

**Sim, em princípio é**, e vale entender por que hoje ele é pequeno.

O consumidor confia cegamente no conteúdo da mensagem: `idConta`, `valor` e `origemAgencia`.
Quem conseguir publicar em `iceibank.eventos` com a routing key `agencia.1.creditar` cria
dinheiro do nada na agência 1, e ainda escolhe o vetor que quiser. O JWT não protege esse
caminho porque a mensagem nunca passa pelo `JwtFilter`. No Sprint 1, a rota
`/creditar-remoto` exigia um token de tipo `SISTEMA`; com o fim da rota, esse controle
também saiu (`TipoToken.SISTEMA` foi removido).

**Quem consegue publicar hoje?** Quem tem a AMQP URL. Ela contém usuário e senha do vhost no
CloudAMQP, e as três agências usam **a mesma** credencial, com permissão total de
configurar, ler e escrever. Na prática, hoje sou só eu: a instância é minha, a URL não está
no repositório (só na variável `RABBITMQ_URL`), e a conexão é `amqps` (TLS). A fronteira de
confiança só mudou de lugar: saiu do segredo do JWT e foi para a credencial do broker. Se a
URL vazar, por exemplo num print ou num commit, qualquer pessoa vira uma "agência".

Como fechar isso num sistema real:

- **Um usuário de broker por agência, com permissões mínimas.** No RabbitMQ, as permissões
  de *write* e *read* são por expressão regular, e as *topic permissions* restringem as
  routing keys. A agência 1 só leria `fila-agencia-1` e só publicaria em `agencia.0.*` e
  `agencia.2.*`.
- **Validar o remetente.** Preencher a propriedade `user_id` da mensagem (o RabbitMQ rejeita
  se não bater com o usuário autenticado na conexão) e conferir no consumidor que
  `origemAgencia` corresponde a ele.
- **Assinar a mensagem** com um token de sistema, como era o JWT `SISTEMA`, ou um HMAC do
  corpo. O consumidor rejeita, de preferência para a fila de mortas, o que não estiver
  assinado.

## Parte D — Linha do tempo causal (seção 8.3)

Resumo da execução (`linha-do-tempo-causal.png`): 18 eventos, 95 pares de eventos de
agências diferentes, **52 concorrentes** e 43 causalmente relacionados. As duas
transferências entre agências aparecem na seção de conferência como
`ANTES (aconteceu-antes, NAO concorrente)`, tanto débito → crédito quanto envio → crédito, e
nenhum desses pares aparece na lista de concorrentes (tarefa 3 da seção 8.2).

### 1. O que, no relógio vetorial, torna possível essa comparação confiável?

O vetor guarda **quantos eventos de cada processo** fazem parte da história causal do
evento: `V(e)[j]` é o número de eventos da agência `j` que "aconteceram antes" de `e`, que
`e` conhece direta ou indiretamente. Com isso vale a equivalência nas duas direções:

> `a → b` **se e somente se** `V(a) < V(b)` (≤ em toda posição e diferente).

O Lamport só garante a ida (`a → b ⇒ L(a) < L(b)`). A volta falha porque o relógio de Lamport
espreme toda essa informação num único número: depois do `max`, não dá mais para saber
*de qual processo* veio cada incremento. Dois eventos com `L = 5` e `L = 8` podem ser
causalmente ligados ou não, e o número não diz qual dos dois casos é. O vetor mantém uma
coordenada por processo, então "b viu tudo que a viu" vira uma verificação posição a posição,
e a ausência dessa relação nos dois sentidos é a prova de concorrência.

### 2. Um par que o script classificou como concorrente. Faz sentido?

**Par 1 (o caso planejado da tarefa 1):**

```
[agencia-0] DEPOSITO [3,0,0]  x  [agencia-1] DEPOSITO [0,2,0]
```

São os depósitos de `demo.sh concorrentes`: três `curl` disparados em paralelo, um para cada
agência, em contas diferentes (3, 1 e 2). Nenhuma mensagem foi trocada entre eles. O vetor
da agência 0 tem 0 na posição da agência 1, e vice-versa: nenhum dos dois sabia da
existência do outro. **Faz sentido**: eles poderiam ter acontecido em qualquer ordem, ou
literalmente ao mesmo tempo, sem mudar o resultado de nenhum.

**Par 2 (mais interessante, porque contraria o relógio de parede):**

```
[agencia-1] TRANSFERENCIA_CREDITO_REMOTO [7,3,0]   (09:49:32.628)
     x  [agencia-0] TRANSFERENCIA_DEBITO [8,0,0]   (09:50:02, da 2a transferencia)
```

Pela hora de parede, o crédito da primeira transferência aconteceu **30 segundos antes** do
débito da segunda. Mesmo assim, o script diz que são concorrentes, e está certo. O fluxo é
sempre de 0 para 1: a agência 0 publica e a 1 consome, sem nenhuma resposta ou confirmação
de volta. Por isso a posição 1 do vetor da agência 0 é **sempre 0**: nada do que a agência 1
fez chegou ao conhecimento da agência 0. O segundo débito dependeu apenas do saldo da conta
0, que o primeiro crédito (na conta 1, em outra agência) não poderia ter alterado. "Antes no
relógio" não é "causa". É exatamente a distinção que o Lamport não consegue fazer.

O par de controle confirma que o script não marca tudo como concorrente: o débito `[8,0,0]`
e o crédito correspondente `[9,6,0]`, ligados pela mensagem `614b73c3`, aparecem como
`ANTES`.

### 3. O algoritmo é O(n²). Seria um problema com milhões de eventos? Como escalar?

**Seria inviável.** Com os 18 eventos desta execução já são 153 pares, 95 deles entre
agências diferentes. Com 1 milhão de eventos seriam ~5 × 10¹¹ comparações, cada uma
percorrendo o vetor inteiro, e a saída (listar todos os pares concorrentes) também seria
quadrática e inútil de ler.

O que daria para fazer, do mais simples ao mais estrutural:

- **Usar o fato de que cada agência é sequencial.** Os eventos de uma mesma agência estão
  totalmente ordenados e seus vetores só crescem. Para um evento `e` da agência `i`, os
  eventos concorrentes da agência `j` formam um **intervalo contíguo** da sequência de `j`:
  começa depois do último evento de `j` que `e` conhece (`e.V[j]`) e termina antes do
  primeiro evento de `j` que conhece `e` (`V[i] >= e.V[i]`). Os dois limites saem por busca
  binária, o que dá O(n log n) por par de agências. E para **contar** concorrentes nem é
  preciso listá-los.
- **Perguntar só o que interessa.** Na prática, concorrência importa entre operações que
  tocam o **mesmo dado** (a mesma conta). Indexar os eventos por conta e comparar só dentro
  de cada grupo reduz n drasticamente. É o que um banco de dados faz para detectar conflitos.
- **Janela de tempo.** Eventos muito distantes no relógio de parede quase sempre já estão
  ligados causalmente por alguma mensagem intermediária. Analisar por janelas, ou de forma
  incremental conforme o log chega, limita o tamanho de cada lote.
- **Paralelizar.** As comparações são independentes entre si, então dá para particionar por
  par de agências ou por conta e distribuir (map/reduce, Spark).

## Funcionalidade adicional (seção 2.1) — dead-letter queue

**O que é:** cada `fila-agencia-N` é declarada com `x-dead-letter-exchange =
iceibank.mensagens-mortas`. Quando o consumidor não consegue aplicar um crédito (conta
inexistente, ou corpo da mensagem inválido), ele lança
`AmqpRejectAndDontRequeueException`: a mensagem é **rejeitada sem voltar para a fila**, e o
próprio RabbitMQ a move para `fila-agencia-N.mortas`, preservando o corpo, o `messageId` e o
cabeçalho `x-death` com o motivo. Junto vêm:

- `POST /mensagens-mortas/reprocessar` (protegida por JWT): devolve as mensagens mortas da
  agência para a fila principal. Cada uma só sai da fila de mortas (`ack`) depois do publisher
  confirm da republicação; se algo falhar no meio, ela continua lá. O reprocessamento move no
  máximo o que havia na fila no início, então uma mensagem que voltar a falhar espera a
  próxima rodada em vez de entrar em loop.
- `/status` passou a mostrar quantas mensagens há em cada fila e em cada fila de mortas.

**Evidência** (`funcionalidade-adicional.png`, continuação direta da resiliência):

```
fila-agencia-1.mortas: 1                         <- o crédito de R$ 10 que não achou a conta
POST /contas  {"id":1, ...}            HTTP 201  [Vetor [9,5,0]] CRIAR_CONTA
POST /mensagens-mortas/reprocessar     HTTP 200  {"reprocessadas":1}
[Vetor [9,6,0]] TRANSFERENCIA_CREDITO_REMOTO {idTransferencia=614b73c3-..., novoSaldo=10.00}
fila-agencia-1.mortas: 0
```

O crédito feito com a agência fora do ar, que tinha falhado depois do reinício, finalmente
chegou à conta 1. O `idTransferencia` é o mesmo do débito na agência 0, e o
`vetorRecebido` continua `[9,0,0]`: a causalidade original foi preservada.

**Por que escolhi esta:** ela ataca exatamente o buraco que a Parte C expõe. O roteiro
mostra a mensagem chegando e se perdendo por falta da conta. Com a dead-letter queue, "não
deu para aplicar agora" deixa de significar "perdido para sempre" e passa a significar
"guardado, visível e reprocessável". É um comportamento novo e observável, que usa um recurso
de verdade do broker (DLX), e não um ajuste cosmético. Não resolve a consistência: o dinheiro
continua fora das duas contas até alguém reprocessar, e a origem continua sem saber. Mas
transforma uma perda silenciosa em uma pendência que um operador consegue ver e resolver.
Quem fecha o ciclo automaticamente é a compensação do Sprint 4.

## Continuidade do Sprint 1 (regressão verificada)

- **JWT** (`regressao-jwt.png`): sem token → 401; token válido → 200; senha errada → 401;
  token expirado → 401. A antiga rota interna `/contas/0/creditar-remoto` agora responde
  **404**. Antes desta mudança, rota inexistente caía no tratador genérico e virava 500;
  corrigido no `TratadorDeErros`.
- **Particionamento**: `POST /contas` com id 1 na agência 0 → 400 "Conta 1 nao pertence a
  esta agencia" (`preparacao-contas.png`).
- **Frontend** (`frontend-transferencia-assincrona.png`): testado pelo navegador (Chrome
  dirigido pelo DevTools Protocol, sem extensões). Login da Ana na agência 0, transferência
  de R$ 5,00 da conta 0 para a conta 2 (agência 2). A tela informou "TRANSFERENCIA ENTRE
  AGENCIAS (...) publicada no RabbitMQ", com o vetor do evento `[11,0,0]`. As pílulas de
  status mostram a agência 2 em `[11,0,3]` (ela consumiu a mensagem), e o histórico da conta
  0 lista os eventos com os vetores. O frontend só precisou trocar "Lamport" por "vetor" nos
  rótulos.
- **Testes automatizados**: 37 testes (`cd agencia && ./mvnw test`), entre eles as três
  regras do relógio vetorial, a sequência do roteiro (`[1,0,0] → [2,0,0] → [2,1,0]`),
  restauração do vetor a partir do log, concorrência com 8 threads, publicação com a routing
  key e o vetor certos, estorno quando a publicação falha e crédito remoto para conta
  inexistente. Os testes de serviço substituem o RabbitMQ por um mock; o caminho real pelo
  broker foi verificado pelas execuções acima.

## Declaração de uso de IA

Conforme a nota de transparência do roteiro: a implementação do Sprint 2 (código, scripts,
testes e o texto destas respostas) foi desenvolvida com apoio do **Claude Code (Anthropic)**,
a partir do código do Sprint 1 e do roteiro. As execuções e evidências são reais, rodadas
contra a minha instância CloudAMQP. Revisei o código entregue e consigo explicar cada parte,
em especial as três decisões listadas no início desta seção e o motivo de cada par
concorrente/causal citado na Parte D.
