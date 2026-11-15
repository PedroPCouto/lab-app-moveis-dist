# ICEIBank — RESPOSTAS (Sprints 1, 2 e 3)

Linguagem escolhida para os 4 sprints: **Java (Spring Boot)**.

- **Sprint 1** (U2 — REST/MVC + relógio de Lamport + JWT): seções logo abaixo.
- **Sprint 2** (U3 — mensageria RabbitMQ + relógio vetorial): [mais abaixo](#sprint-2--mensageria-pubsub-e-relógio-vetorial).
- **Sprint 3** (U4 — app Flutter + eleição de líder (Bully) + offline-first): [ao final do arquivo](#sprint-3--app-flutter-eleição-de-líder-e-offline-first).

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

---

# Sprint 3 — App Flutter, eleição de líder e offline-first

Projeto: ICEIBank, Sprint 3 (U4 — Desenvolvimento Móvel: app Flutter + consenso por
eleição de líder com o algoritmo de Bully + sincronização offline-first com o relógio
vetorial do Sprint 2). O backend continua em Java/Spring Boot (regra 2.2 do escopo); o
código Node do roteiro foi traduzido, mantendo rotas e formato de mensagem.

Todas as saídas citadas vêm das execuções reais de **15/11/2026** registradas em
`evidencias/sprint3/` (prints) e `evidencias/sprint3/saidas/` (texto). As horas de parede
dos logs (`horaParede`) estão em UTC; o `Get-Date` dos prints está no horário de Brasília
(UTC−3). As agências rodaram sem RabbitMQ nesta máquina (sem a `RABBITMQ_URL`): nada do
Sprint 3 depende do broker, e a única consequência visível é a transferência **entre**
agências responder 503 com estorno (ver "Continuidade", no fim).

## Como a arquitetura ficou

| Peça | Onde | O que faz |
|---|---|---|
| `BullyService` | `agencia/.../service/BullyService.java` | eleição, anúncio de coordenador, heartbeat |
| `ClienteBully` / `ClienteBullyHttp` | `service/` | chamadas entre agências com `java.net.http.HttpClient` e timeout |
| Rotas `/bully/eleicao`, `/bully/coordenador`, `/bully/lider` | `controller/BullyController.java` | públicas (sem JWT), como no roteiro |
| `HistoricoLideranca` | `tools/HistoricoLideranca.java` | versão Java do `historico-lideranca.js` (`./mvnw -q exec:java@historico-lideranca`) |
| `POST /contas/{id}/sincronizar` | `SincronizacaoService`, `ContaService.aplicarSincronizado` | aplica operação offline só se `vetorConhecido == vetor atual`; senão 409 |
| `ContaComVetor` | `dto/` | toda leitura/escrita de conta devolve `vetorAtual` junto com o saldo |
| App Flutter | `app_mobile/lib/src/` | fluxo bancário, painel da líder, offline-first, painel do cluster |
| Testes | `BullyServiceTest`, `SincronizacaoServiceTest` (Java, 61 no total); `banco_offline_test.dart` (10); `e2e/sprint3.spec.js` (8, Playwright) | |

Decisões que vão além do exemplo em Node (e por quê):

1. **O "OK" do Bully volta na hora.** Ao receber `/bully/eleicao`, a agência responde 200
   e só depois conduz a própria eleição, numa thread separada. Se ela conduzisse a eleição
   antes de responder (até 1 s por agência maior), quem perguntou estouraria o timeout de
   1 s e se declararia líder por engano.
2. **Concorrência explícita.** `liderAtual` é um `AtomicInteger` e "eleição em andamento"
   um `AtomicBoolean` com `compareAndSet` — o heartbeat roda numa thread agendada enquanto
   as mensagens chegam nas threads do Tomcat (seção 11 do roteiro).
3. **Anúncio de quem tem id menor é recusado.** Se a agência 1 se anuncia líder para a 2
   (que estava subindo e não respondeu a tempo), a 2 não aceita: faz a própria eleição e
   vence. Sem isso a tempestade da subida pode convergir para a agência errada (teste
   `anuncioDeQuemTemIdMenorEhRecusadoEProvocaNovaEleicao`).
4. **Heartbeat confere também o conteúdo da resposta**: se a líder responde mas diz que
   não é mais líder (reiniciou e está no meio da própria eleição), reelege. E usa
   `scheduleWithFixedDelay`, para uma verificação lenta nunca se sobrepor à seguinte.
5. **Toda mensagem do Bully passa pelo relógio vetorial e é logada, inclusive os envios**
   (`ELEICAO_ENVIADA`, `COORDENADOR_ENVIADO`). O relógio é restaurado do último evento
   logado quando a agência reinicia (Sprint 2); envio não logado faria a agência reutilizar
   vetores que as outras já viram. Observado: a agência 2 derrubada às 16:04 voltou com o
   vetor `[4,8,20]`, continuando o log.
6. **Sincronização com compare-and-increment.** A comparação "vetor conhecido == vetor
   atual" e o incremento do evento acontecem sob a mesma trava do relógio
   (`RelogioVetorial.eventoLocalSeIgual`), então nenhum outro evento entra entre a
   verificação e a aplicação. Saldo e vetor de uma conta são lidos juntos, com a conta
   travada, para o par que o app guarda ser consistente.
7. **Idempotência por `idOperacao`.** Se a resposta de uma sincronização se perde, o app
   reenvia; sem o id, o reenvio daria 409 (o vetor já andou pela própria operação) e a
   pessoa veria "conflito" numa operação que já foi aplicada.
8. **Transferência também sincroniza** (o roteiro deixava como extensão): mesma comparação
   de vetor sobre a conta de origem; o resto é a transferência normal.

---

## Parte A — Mensagens de eleição e coordenador (seção 5.5)

Evidências: `eleicao-inicial.png` (as 3 sobem juntas), `eleicao-escalonada.png` (sobem
com 4 s de intervalo, como abrir 3 terminais um depois do outro).

### 1. Por que o Bully elege o maior ID, e não o menor? O resultado seria diferente (mas correto) com a regra invertida?

O "maior" não tem nada de especial: o que o algoritmo precisa é de uma **ordem total**
entre os processos que todos conheçam de antemão, para que todos cheguem à mesma conclusão
sem votar. "Maior ID vence" é a convenção do artigo de Garcia-Molina — o nome *bully*
("valentão") vem daí: o maior sempre se impõe sobre os menores.

Com a regra invertida (menor ID vence, eleição enviada aos de ID **menor**), o algoritmo
continuaria correto: todos convergiriam para a menor agência ativa. O resultado seria
diferente — no nosso cluster a líder seria a agência 0 em vez da 2 — mas igualmente único e
determinístico. O que não pode é misturar as duas regras: se uma agência pensasse "maior
vence" e outra "menor vence", cada uma aceitaria um coordenador diferente.

Na prática, o critério pode carregar significado: o ID pode codificar "a máquina mais
forte" ou "a mais nova" e aí a escolha da ordem passa a importar. Aqui os IDs são só
posições, então tanto faz.

### 2. Mais de uma agência declarou `TORNOU_LIDER` na subida. Bug ou comportamento esperado?

Esperado — e as duas execuções mostram dois jeitos diferentes de isso acontecer:

- **Subida escalonada** (`eleicao-escalonada.png`, 16:40): a agência 0 subiu sozinha,
  mandou eleição para 1 e 2 (ninguém respondeu) e às 16:40:23 declarou `TORNOU_LIDER` com o
  vetor `[4,0,0]`. A 1 subiu 4 s depois, perguntou só à 2 (fora do ar), declarou
  `TORNOU_LIDER` às 16:40:33 (`[0,3,0]`) e anunciou para a 0, que aceitou. A 2 subiu por
  último e declarou às 16:40:42 (`[0,0,2]`). **Três agências diferentes se declararam
  líder**, e cada declaração estava certa no momento em que foi feita: era a maior agência
  *ativa que ela conseguia ver*.
- **Subida simultânea** (`eleicao-inicial.png`, 15:48): como as três subiram juntas, a 2
  já respondia quando 0 e 1 perguntaram — só ela se declarou líder, mas **4 vezes** entre
  15:48:09.102 e 15:48:09.572. Cada `ELEICAO_RECEBIDA` (de 0 e de 1, inclusive as
  disparadas pelos próprios anúncios) fez a 2 rodar uma nova eleição, ganhar de novo e
  anunciar de novo; 0 e 1 registraram 4 `LIDER_ANUNCIADO` cada, todos com `liderId=2`.

Não é bug porque o Bully não promete "uma única declaração"; promete que, quando as
mensagens param de chegar, **todas concordam no maior ID ativo**. Nas duas execuções as 3
responderam `liderAtual: 2` em menos de 1 s depois da última subida. Seria bug se ficassem
duas líderes ao mesmo tempo de forma estável — e é exatamente o caso que a decisão 3 acima
previne.

### 3. `/bully/eleicao` não tem autenticação. É um problema de segurança real para este projeto?

Neste sprint o impacto é pequeno, mas o problema é real e cresce no Sprint 4:

- **Hoje:** a líder é só informativa (escopo, "o que este sprint não resolve"). Um processo
  que descubra a URL pode disparar eleições em loop (`POST /bully/eleicao` não custa nada
  para quem envia, e cada uma gera mensagens para as outras agências — um vetor de negação
  de serviço barato) ou, pior, mandar `POST /bully/coordenador {"liderId": 2}` dizendo que
  uma agência morta é a líder. A agência aceitaria (só recusa anúncio de ID **menor** que o
  dela) até o heartbeat seguinte perceber que a "líder" não responde. Nenhum saldo muda.
- **No Sprint 4** a líder vai coordenar transações distribuídas (2PC). Aí um coordenador
  falso poderia segurar transações ou receber votos de commit — o impacto passa a ser sobre
  dinheiro.

O JWT de cliente não resolve, porque quem chama é outra agência, não uma pessoa. O caminho
seria autenticar **agências**: um token de serviço assinado com uma chave só das agências
(o tipo `SISTEMA` que existia no Sprint 1), mTLS entre elas, ou simplesmente não expor as
portas do Bully fora da rede interna — que é o que os containers do Sprint 4 permitem
fazer.

---

## Parte B — Heartbeat e failover automático (seção 6.4)

Evidência: `failover.png` (16:04). A líder 2 foi encerrada com `Stop-Process`; às
16:04:46.880 a agência 1 registrou `LIDER_NAO_RESPONDEU {liderAtual=2}`, 8 ms depois
`TORNOU_LIDER`, e às 16:04:46.897 a 0 aceitou o anúncio. Ninguém tocou em nada. Religada
(passo 6), a 2 voltou, fez a própria eleição e retomou a liderança às 16:05:02.

### 1. Heartbeat de 100 ms? E de 60 s?

- **100 ms:** detecção quase instantânea, mas cada seguidora faria 10 `GET /bully/lider`
  por segundo à líder — com N agências, ~10·(N−1) requisições/s só de "você está viva?".
  Pior que o tráfego é a **sensibilidade**: o intervalo fica menor que variações normais
  da rede ou uma pausa do coletor de lixo da JVM. Uma resposta atrasada vira "líder caiu",
  e o sistema faz eleições desnecessárias (*flapping*), cada uma com sua rajada de mensagens
  e trocas de liderança.
- **60 s:** quase nenhum tráfego, mas o cluster pode passar até um minuto achando que tem
  líder quando não tem. Neste sprint isso só deixaria o painel errado; no Sprint 4, seriam
  60 s de transações sem coordenador.

O valor certo depende de quanto custa ficar sem líder versus quanto custa uma eleição à
toa. 3 s (o padrão do roteiro) é um meio-termo razoável para uma rede local. Por isso
deixei configurável (`BULLY_HEARTBEAT_MS`, `BULLY_TIMEOUT_MS`), sem recompilar.

### 2. A agência que volta "toma" a liderança de volta. Isso é desejável?

No Bully clássico, sim por definição — a regra é "o maior ID ativo". No nosso teste: a 1
liderava desde 16:04:46 sem problema nenhum, e às 16:05:02 a 2 voltou e tomou a liderança.
Foi uma troca que **ninguém precisava**: o sistema estava saudável.

Num sistema real isso é um problema prático: se a líder coordena uma transação (o 2PC do
Sprint 4) e no meio dela uma agência de ID maior volta e se anuncia, a transação fica com
dois "coordenadores" possíveis — a antiga, que já mandou o *prepare*, e a nova, que não
sabe de nada. Participantes podem ficar bloqueados esperando um *commit/abort* que a nova
líder não vai mandar. Também há o caso da agência instável, que cai e volta várias vezes:
a cada volta ela derruba uma liderança estável.

Alternativas: não fazer *preemption* (quem volta só aceita a líder atual e espera uma
falha real para disputar), usar um período de "aquecimento" antes de concorrer, ou usar
épocas/termos de liderança (como no Raft) para que mensagens de uma liderança antiga
sejam reconhecidas e descartadas.

### 3. Rede lenta (não fora do ar) com resposta acima de 1 s?

O mesmo `TIMEOUT_MS` não distingue "morta" de "lenta" — para o `ClienteBully` as duas são
"não respondeu". Consequências:

- No **heartbeat**, uma líder viva mas lenta é dada como morta: as seguidoras fazem uma
  eleição sem necessidade.
- Na **eleição**, se a agência de ID maior responde em 1,2 s, quem perguntou já desistiu
  dela e se declara líder; a maior, que recebeu a mensagem, faz a própria eleição e também
  se declara. Por algum tempo há **duas agências achando que são líderes** — o que o
  Bully assume que não acontece, porque o algoritmo foi pensado para um sistema
  **síncrono** (atrasos limitados e conhecidos).

Nesta implementação, o estrago é contido por duas coisas: a recusa de anúncio de ID menor
(a maior, ao receber o anúncio da menor, faz eleição e se impõe) e o heartbeat que confere
o conteúdo da resposta. Mas, enquanto a rede continuar lenta, o cluster pode oscilar.
Mitigações: timeout maior que a latência esperada (configurável aqui), exigir algumas
falhas seguidas antes de declarar a líder morta, ou um algoritmo que não dependa de
sincronia para ser seguro (Raft/Paxos usam maioria e termos, não timeouts, para garantir
uma líder só).

---

## Parte C — Observando a eleição (seção 7.3)

Evidências: `historico-lideranca.png` (subida simultânea + failover + volta da 2) e
`historico-escalonada.png` (subida escalonada).

### 1. Quantas vezes a liderança mudou de mãos na tempestade da subida? Isso tem custo?

Depende de como as agências sobem, e o `HistoricoLideranca` separa as duas contagens:

- **Subida simultânea:** 1 troca efetiva (ninguém → 2), mas **4 declarações**
  `TORNOU_LIDER` da própria 2. O histórico completo da execução (subida + failover + volta)
  teve 31 eventos de eleição, 6 declarações e 3 trocas efetivas (2 → 1 → 2).
- **Subida escalonada:** 3 trocas efetivas na subida (0 → 1 → 2), 9 eventos de eleição.

Mesmo sem mudar o resultado final, há custo real:

- **Rede:** cada eleição recebida gerou uma eleição nova, e cada declaração da 2 gerou 2
  anúncios. Contando nos logs da subida simultânea das 15:48: 7 eleições iniciadas e
  **12 mensagens HTTP entre agências** (4 de eleição + 8 de coordenador), quando 5 (3
  eleições + 2 anúncios) bastariam.
- **Tempo:** quem pergunta a uma agência fora do ar espera o timeout (1 s por agência). Na
  subida escalonada, a 0 e a 1 foram líderes por ~10 s cada — tempo em que o resto do
  sistema confiou numa líder que seria trocada logo em seguida.
- **Trabalho jogado fora** (relevante no Sprint 4): uma líder que começa a coordenar algo e
  é substituída segundos depois deixa esse trabalho para ser desfeito ou retomado.
- **Relógio vetorial e logs:** cada mensagem incrementa o vetor. Isso tem um efeito
  colateral visível na Parte E: uma eleição muda o vetor da agência e, portanto, faz
  qualquer operação offline pendente dar conflito, mesmo sem ninguém ter mexido na conta.

---

## Parte D — App Flutter (seção 8.3)

Evidências: `app-lider-atual.png`, `app-failover.png`, `app-fluxo-bancario.png` e o vídeo
`videos/sprint3-app.webm`. O app foi capturado como **build web num Chromium com viewport
de celular (412×915)**: esta máquina não tem emulador Android (nem espaço em disco para
um). O mesmo código compila para Android (projeto `android/` gerado, com permissão de
internet e HTTP em texto claro para a rede do laboratório) — `flutter run` no emulador usa
`10.0.2.2` automaticamente.

No failover visto pelo app (`app-failover.png`, execução final das 16:39): às 16:39:19 o
painel mostrava "Agência 2"; a agência 2 foi encerrada às 16:39:20 e, sem tocar na tela, o
painel passou a "Agência 1, confirmado pela agência 0" — o histórico do app registra a
troca às 16:39:21, e o `Invoke-RestMethod` das 16:39:25 confirma `liderAtual 1` nas
agências 0 e 1.

### 1. Como o app decide a frequência de `/bully/lider`? Que trade-off?

A cada **3 s** (`Intervalos.pollingLider`), o mesmo intervalo do heartbeat das agências.
Consultar mais rápido que isso não mostra a troca mais cedo: a própria agência consultada
só descobre a nova líder depois do heartbeat dela (até 3 s + timeout + eleição). O atraso
total visto pelo usuário fica entre ~1 e ~7 s; no teste foram ~1 a 2 s.

O trade-off no celular é rede e bateria: cada consulta acorda o rádio. Por isso o app:
pergunta a **uma** agência por vez (não às 3), começando pela que respondeu da última vez;
usa timeout curto (1,5 s) para não segurar conexões; e só faz polling enquanto a tela
inicial está aberta (`Lideranca.parar()` no `dispose`). Num app de produção, eu também
pararia o polling com o app em segundo plano (`AppLifecycleState.paused`) ou trocaria por
*push* (WebSocket/FCM) avisando só quando a líder muda.

### 2. Duas agências respondendo líderes diferentes numa transição: como o app se comporta?

O app confia na **primeira agência que responder** e mostra também quem informou
("confirmado pela agência 0 às 16:39:37"). Se ela ainda não soube da nova eleição, o painel
mostra a líder antiga, ou "Eleição em andamento" quando `liderAtual` vem nulo. Na consulta
seguinte, 3 s depois, a agência já terá recebido o anúncio e o painel se corrige sozinho.
O histórico de trocas (funcionalidade adicional) registraria a ida e a volta, se houvesse.

Não é um problema real **para um painel informativo**: é consistência eventual, e o
algoritmo garante que as agências convergem. Seria um problema se o app usasse a líder para
**decidir** algo (por exemplo, para onde mandar uma transação no Sprint 4). Aí a resposta
de uma agência só não basta: o app teria de confirmar com a própria líder ("você é a
líder?", que é o que o heartbeat das agências faz) ou deixar a agência de entrada
encaminhar a operação para a líder. O app não tenta "votar" entre respostas: com 3
agências e uma delas fora do ar, não há maioria confiável.

### 3. Flutter × frontend web do Sprint 1: o que foi reaproveitável e o que mudou?

**Reaproveitado (conceitos):** a mesma API REST, sem nenhuma rota nova para o fluxo
bancário; o mesmo particionamento no cliente (conta `N` → agência `N % 3`, porta
`4000 + OFFSET + N`); o JWT guardado e enviado em toda requisição, válido nas 3 agências;
o tratamento de 401 voltando para o login; a separação em camadas — no web,
`modelo.js` / `visao.js` / `controlador.js`; no app, `api.dart` + `banco.dart` (modelo e
controle, `ChangeNotifier`) e `telas/` (visão, redesenhada por `ListenableBuilder`).

**Pensado de outro jeito por ser mobile:**

- **Rede não é garantida.** No web, sem rede a página simplesmente falha. No celular, cair
  da rede é normal — daí o offline-first (Parte F) e o erro de rede ser um caso de primeira
  classe (`ErroDeRede` × `ErroApi`).
- **Armazenamento:** o web guarda só o token em `localStorage`; o app guarda sessão, contas
  e fila no Hive, que precisa sobreviver ao sistema matar o app.
- **Endereço do servidor:** no navegador, `localhost` é o PC; no emulador é `10.0.2.2`; num
  celular é o IP do PC. O host virou configuração (tela de login / `--dart-define`).
- **Atualização automática:** o web só relê o estado ao clicar em "Atualizar"; o app faz
  polling sozinho (líder, contas, agências), com o cuidado de bateria descrito acima.
- **Dinheiro em centavos (`int`)** em vez de `double`, e entrada de valores no formato
  brasileiro ("25,50").
- **Interface em toque:** diálogos por operação em vez de um formulário grande; erros em
  SnackBar; conflitos em diálogo modal.

---

## Parte E — Sincronização offline-first no backend (seção 9.6)

Evidência: `conflito-sincronizacao.png` (16:12:43): conta 30 lida com
`vetorAtual [40,49,79]`; depósito direto de 50 → `[41,49,79]`; sincronizar um saque de 30
com o vetor antigo → **409** `relacao: ANTES`, `vetorConhecido [40,49,79]`,
`vetorAtual [41,49,79]`, `contaAtual.saldo 150.00`; reenviar com `[41,49,79]` → 200, saldo
120,00 e vetor `[42,49,79]`.

### 1. Por que comparar o vetor inteiro e não só verificar se o saldo mudou?

Porque o saldo é só o **resultado** das operações; o vetor registra **que operações
aconteceram**. Comparar saldo deixa passar dois casos:

- **Mudanças que se anulam (ABA):** enquanto o app estava offline, entrou um depósito de 50
  e saiu um saque de 50. O saldo é igual ao que o app viu, mas o estado não é — duas
  operações que o usuário não conhece aconteceram. Pela comparação de saldo, "nada mudou".
  O vetor avançou duas posições e acusa.
- **Mudanças em outros campos ou contas que afetam a decisão,** sem mexer neste saldo: a
  comparação de saldo só olha um número; o vetor cobre qualquer evento da agência (que,
  como discutido na pergunta 2, é amplo até demais).

Além disso, o vetor diz **como** os estados se relacionam: `ANTES` (o app está
desatualizado — o caso normal), `DEPOIS` (o app conhece um estado mais novo que a agência —
por exemplo, a agência reiniciou e perdeu eventos; teste
`vetorDoAppAFrenteDaAgenciaTambemEhConflito`) ou `CONCORRENTES`. Saldo nenhum dá isso.

### 2. Qualquer mudança no vetor da agência vira conflito, mesmo em outra conta. Por que essa simplificação existe e como torná-la precisa por conta?

Existe porque o relógio vetorial do Sprint 2 é **por agência** (um contador por processo),
não por conta: um depósito na conta 33 incrementa a mesma posição que um depósito na conta
30. O vetor sabe que "algo aconteceu na agência 0", mas não em qual conta. Teste
`eventoEmOutraContaDaMesmaAgenciaTambemEhConflitoSimplificacaoDoRoteiro`. Na prática é ainda
mais amplo do que o roteiro sugere: **eventos do Bully também incrementam o vetor**. Uma
eleição depois da queda de uma líder muda o vetor das três agências, e qualquer operação
offline pendente vai dar 409 mesmo que ninguém tenha tocado na conta.

O resultado é seguro (nunca aplica às cegas), só que pessimista: gera conflitos falsos, que
custam uma decisão da pessoa usuária. Para tornar a detecção precisa por conta:

- **Versão por conta:** cada conta guarda um contador de versão (ou o vetor do último
  evento que a alterou). O app guarda essa versão; a sincronização compara só ela. É o
  *optimistic locking* clássico (`version` / `ETag` + `If-Match`).
- **Vetor por conta:** se a conta pudesse ser alterada em mais de um lugar (réplicas), cada
  conta teria seu próprio vetor de versões — é o que bancos de dados como o Dynamo/Riak
  fazem por chave.
- Mais fino ainda: **verificar a regra de negócio** em vez da versão — um depósito é
  comutativo e poderia ser aplicado mesmo com outra mudança no meio; um saque só precisa
  que o saldo atual ainda cubra o valor. Isso troca "detectar conflito" por "resolver
  conflito", o que o roteiro deixa explicitamente fora do escopo.

### 3. No 409, o app deveria descartar, tentar de novo automaticamente ou perguntar?

**Perguntar** — foi o que implementei, com uma exceção mecânica:

- **Descartar** sozinho perde a intenção da pessoa sem avisar: ela fez um depósito e ele
  simplesmente some. Inaceitável num app bancário.
- **Tentar de novo automaticamente** com o vetor novo é "aplicar às cegas" com um passo a
  mais: o 409 existe justamente porque o estado que a pessoa viu ao decidir não vale mais.
  Ela decidiu sacar 10 com R$ 225,50 na tela; talvez com o saldo real (R$ 275,50, ou R$ 5)
  a decisão fosse outra.
- **Perguntar**, mostrando o que ela viu e o que é real, devolve a decisão a quem tem
  contexto para tomá-la.

A exceção: quando duas operações offline seguidas da mesma conta partiram do mesmo vetor e
a primeira foi aceita, a segunda é enviada com o vetor devolvido pela primeira — o único
evento novo é a própria operação da pessoa, que ela já conhece. Isso não é um retry de
conflito; é o app atualizando o que sabe.

---

## Parte F — App Flutter offline-first (seção 10.3)

Evidências (execução final, 16:39): `app-offline.png` (16:39:34 — depósito de R$ 25,00 em
modo avião aparece como PENDENTE, com saldo provisório R$ 225,50 e o vetor conhecido
`[16,17,22]`; a agência ainda mostra R$ 200,50 no `Invoke-RestMethod` ao lado; o painel da
líder mostra "nenhuma agência respondeu"), `app-sincronizacao.png` (16:39:35 — só
reconectar: a fila some, o saldo R$ 225,50 passa a "confirmado" e a agência confirma 225,50
com o vetor `[17,17,22]`) e `app-conflito.png` (16:39:36–38). O "modo avião" é o `setOffline(true)` do
Chromium (o `navigator.onLine` vira `false`), detectado pelo `connectivity_plus`.

### 1. Qual biblioteca de armazenamento local e por quê? Alternativas?

**Hive, na versão mantida pela comunidade (`hive_ce`).** Motivos:

- **Funciona igual no Android e no navegador** (arquivos no aparelho; IndexedDB na web).
  Como as evidências foram feitas no build web, isso foi decisivo — o `sqflite` não roda
  na web sem outro pacote.
- **Os dados são pequenos e acessados por chave:** sessão, contas por `dono:id`, fila por
  `idOperacao`. Não preciso de SQL nem de joins.
- **Sem geração de código:** guardo cada registro como JSON (`Box<String>`), sem
  `TypeAdapter` nem `build_runner`.
- O `hive` original está sem manutenção; o `hive_ce` é o fork com a mesma API e suporte às
  versões atuais do Dart.

Alternativas consideradas: **sqflite** (SQL de verdade, transações; mas não roda na web e
pede esquema/migrações para algo que é chave-valor); **Drift** (SQL tipado e reativo,
excelente para um app maior, mas com geração de código e mais peso); **shared_preferences**
(simples, mas pensado para preferências e não para uma fila de operações);
**Isar/ObjectBox** (rápidos, mas mais complexos do que o problema pede).

Ressalva honesta: o token JWT também fica no Hive, sem criptografia. Num app real ele iria
para o `flutter_secure_storage` (Keystore/Keychain).

### 2. O que aconteceu na tela quando a sincronização foi recusada? Por que essa abordagem?

Sequência registrada em `app-conflito.png` (16:39:36–16:39:38):

1. Offline, saque de R$ 10,00 na conta 30: aparece **"R$ 215,50 — PROVISÓRIO, 1
   pendente"** e a SnackBar "guardado no aparelho, PENDENTE de sincronização".
2. Pelo PowerShell, com o app ainda offline, depósito de R$ 50,00 direto na API → saldo
   real 275,50, vetor `[18,17,22]`.
3. A conexão volta. O app envia o saque com o vetor que conhecia `[17,17,22]` e recebe 409.
   Abre sozinho um diálogo **"A agência recusou a sincronização"**, explicando que a conta
   mudou enquanto ele estava offline e que **nada foi aplicado**, com: "Saque de R$ 10,00 na
   conta 30 (feita offline às 16:39:36) — NÃO APLICADA", "vetor [17,17,22] -> [18,17,22]",
   "Saldo que você via: R$ 225,50 →
   saldo real agora: R$ 275,50", e três opções: **Aplicar sobre o saldo atual**,
   **Descartar**, **Decidir depois**. O saldo da conta passa a mostrar o real (275,50), sem
   o "provisório" — uma operação em conflito não entra no saldo provisório.
4. Escolhida "Aplicar sobre o saldo atual", o app reenviou com o vetor do 409 e a conta
   ficou em R$ 265,50, confirmado, com o vetor `[19,17,22]`.

Se a pessoa escolhe "Decidir depois", a operação continua num cartão vermelho
"Sincronização recusada" no topo da tela, com os mesmos botões. Operações seguintes da
mesma conta que estavam na fila também param (marcadas "parada: uma operação anterior da
mesma conta entrou em conflito"), porque partiram do mesmo estado desatualizado.

Por quê: o requisito é que o 409 seja **visível**, e a resposta da pergunta E3 — a decisão
é da pessoa. O diálogo modal garante que ela veja no momento em que acontece; o cartão
garante que a decisão não se perca se ela fechar o diálogo; mostrar "o que você viu × o que
é real" dá o contexto para decidir.

### 3. Que risco a UI otimista traz para um app bancário? Como o design reduz esse risco?

O risco é a pessoa **agir com base em dinheiro que ainda não existe** (ou que já não
existe): ver R$ 225,50 depois de um depósito offline e pagar uma conta contando com ele, ou
fazer um saque offline que a agência vai recusar por saldo insuficiente no saldo real. Num
banco, "a tela disse que eu tinha" vira um problema financeiro e de confiança.

Como o design reduz:

- **O provisório nunca se passa por confirmado:** cor diferente, selo "PROVISÓRIO — N
  pendente(s)", e logo abaixo o "último saldo confirmado pela agência". A fila fica no
  topo da tela, com "PENDENTE" e a hora em que cada operação foi feita.
- **Limite local:** offline, não deixa sacar/transferir mais que o saldo provisório.
- **Nada some em silêncio:** recusas (409 ou 400) viram diálogo + cartão; o saldo exibido
  volta ao real na hora.
- **Ordem preservada:** se a conta tem algo na fila, uma nova operação entra atrás dela,
  mesmo online, para não "furar" a fila.
- **A agência tem a palavra final:** o app só envia intenções; quem aplica (e verifica
  saldo e vetor) é a agência, com idempotência para reenvios.
- **O que não fiz offline:** abrir conta. Um sistema real limitaria também o valor das
  operações offline (como os limites de transação de PIX/cartão offline).

---

## Funcionalidade adicional (seção 2.1) — painel do cluster no app

Evidência: `funcionalidade-adicional.png` (16:39:41).

No fim da tela inicial, um cartão **"Agências do cluster"** consulta `GET /status` das 3
agências em paralelo a cada 5 s e mostra, para cada uma, se está no ar, a latência, quantas
contas tem e o vetor atual; a líder tem um ícone. Abaixo, **"Últimas trocas de liderança
(vistas por este app)"**: cada mudança percebida pelo painel da líder vira um registro
(hora, de → para, quem informou), guardado no Hive.

No print, a agência 1 (que não era a líder) foi derrubada: o cartão mostra "2 de 3
respondendo", "Agência 1 — FORA DO AR (sem resposta às 16:39:39)" em vermelho, a 2 com o
ícone de líder; o histórico traz exatamente o failover do teste D3: "16:39:09 líder inicial
→ agência 2", "16:39:21 agência 2 → agência 1", "16:39:30 agência 1 → agência 2". O
`Test-NetConnection` ao lado confirma a porta 4001 fechada.

**Por que escolhi:** o painel da líder responde "quem coordena"; este responde "quem está
vivo" — juntos mostram o heartbeat funcionando visto de fora (a líder fica vermelha aqui e,
segundos depois, o painel de cima troca). É útil para demonstrar o algoritmo em sala sem
abrir terminais, e o histórico deixa visível no celular o que o `HistoricoLideranca`
reconstrói a partir dos logs. Além disso (menor, não conta como a funcionalidade):
heartbeat e timeouts do Bully configuráveis por variável de ambiente.

---

## Continuidade (Sprints 1 e 2, sem regressão)

- **Testes automatizados:** 61 testes Java (`cd agencia && ./mvnw test`), incluindo os 37
  do Sprint 2 sem alteração de comportamento; 10 testes Dart (`flutter test`); 8 testes
  Playwright (`e2e\rodar-sprint3.ps1`).
- **JWT e frontend web** (`regressao-frontend-jwt.png`, 16:39:51): o frontend do Sprint 1,
  sem nenhuma mudança, fez login da Ana e uma transferência local 30 → 33 ("TRANSFERENCIA
  LOCAL (mesma agencia 0) ... Vetor do evento: [22,21,27]"). Sem token, `GET /contas/30` →
  401; com token → 200, agora com `vetorAtual`. Os campos novos (`vetorAtual`) não quebram
  o frontend, que só lê os campos antigos.
- **Mensageria/relógio vetorial:** as regras do vetor não mudaram (só ganharam
  `obterVetorAtual` e o compare-and-increment); o `MesclarLogs` continua lendo os logs, que
  agora também têm eventos do Bully. Uma mudança pequena no `creditarRemoto`: o
  `aoReceber` passou para dentro da trava da conta, para o snapshot (saldo, vetor) ser
  consistente.
- **Mensageria real (CloudAMQP), 15/11/2026 18:47:** a suíte gravada do Sprint 2
  (`e2e/rodar.ps1`) passou os 3 testes com as agências do Sprint 3 (com o Bully ligado):
  transferência entre agências via RabbitMQ, agência de destino fora do ar (a mensagem
  esperou na fila durável) e fila de mortas com reprocessamento. Vídeos em
  `evidencias/sprint3/videos/regressao-sprint2-teste-{a,b,c}.webm`. Um ajuste foi necessário
  no próprio teste, não no código: ele esperava que o vetor da agência 0 fosse `[N,0,0]` na
  hora da transferência. Com o Bully, as três agências trocam mensagens de eleição logo ao
  subir, então a agência 0 já "conhece" eventos das outras e o vetor vira `[N,x,y]`. É o
  relógio vetorial funcionando como deveria (regra 3 aplicada às mensagens do Bully); a
  verificação que importa — a agência 2, ao consumir, passa a ter a posição 0 igual à do
  envio — continua no teste.
