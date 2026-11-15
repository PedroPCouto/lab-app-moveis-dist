# ICEIBank

Banco simplificado dividido em **agências**, usado como projeto único ao longo dos 4 sprints
da disciplina. Cada agência é uma partição independente de contas — a conta `N` pertence à
agência `N % 3`, e nenhuma agência sabe o saldo das contas das outras.

| Sprint | Unidade | Tecnologia | Conceito de SD | Situação |
|---|---|---|---|---|
| 1 | U2 — Desenvolvimento Web | API REST / MVC | Relógio lógico de Lamport | concluído |
| 2 | U3 — Comunicação indireta | Mensageria / Pub-Sub (RabbitMQ) | Relógio vetorial | concluído |
| **3** | U4 — Desenvolvimento Móvel | App Flutter | Consenso (eleição de líder) + offline-first | **este** |
| 4 | U5 — Computação em Nuvem | Containers | Transações distribuídas (2PC/Saga) | — |

Linguagem escolhida para os 4 sprints: **Java 25 + Spring Boot 4.1** (+ Spring AMQP no Sprint 2).
App mobile (Sprint 3): **Flutter 3.50**, em [`app_mobile/`](app_mobile/README.md).

> Continuando o projeto (pessoa ou IA)? Comece por [`AGENTS.md`](AGENTS.md) e
> [`docs/SPRINT3-ESTADO.md`](docs/SPRINT3-ESTADO.md): estado atual, decisões e ambiente.
As respostas às perguntas dos roteiros estão em [`RESPOSTAS.md`](RESPOSTAS.md).

---

## O que mudou no Sprint 3

- **Eleição de líder (Bully)** entre as 3 agências (`BullyService`): ao subir, cada agência
  faz uma eleição; vence a de maior id ativa. Rotas públicas `POST /bully/eleicao`,
  `POST /bully/coordenador` e `GET /bully/lider`. Toda mensagem passa pelo relógio vetorial.
- **Heartbeat** a cada 3 s: se a líder não responde (ou não se reconhece mais como líder),
  as outras fazem uma nova eleição sozinhas. Parâmetros por variável de ambiente
  (`BULLY_HEARTBEAT_MS`, `BULLY_TIMEOUT_MS`, `BULLY_HABILITADO`, `AGENCIAS_HOST`).
- **Histórico de lideranças** (`tools/HistoricoLideranca`): reconstrói "líder inicial →
  queda → nova líder" a partir dos logs.
- **Sincronização offline-first:** toda leitura/escrita de conta devolve `vetorAtual`;
  `POST /contas/{id}/sincronizar` aplica uma operação feita offline só se o vetor que o app
  conhecia ainda é o vetor da agência — senão **409** com o estado real.
- **App Flutter** (`app_mobile/`): fluxo bancário completo, painel da líder atualizado a
  cada 3 s, modo offline-first (Hive + fila + UI otimista + sincronização automática + 409
  visível) e, como funcionalidade adicional, o painel do cluster (agências ativas + histórico
  de trocas de líder).

## O que mudou no Sprint 2

- A transferência **entre agências** não chama mais a outra agência por HTTP: a agência de
  origem publica um `MensagemCredito` na exchange topic `iceibank.eventos` com a routing key
  `agencia.<destino>.creditar`, e a agência de destino consome da sua fila (`fila-agencia-N`)
  quando puder — mesmo que esteja fora do ar no momento da publicação.
- O **relógio de Lamport** foi substituído pelo **relógio vetorial** (`RelogioVetorial`): o
  vetor do envio viaja na mensagem e o destino aplica `max` posição a posição + 1.
- A rota `/contas/{id}/creditar-remoto` e o token interno de `SISTEMA` deixaram de existir.
- **Funcionalidade adicional:** dead-letter queue (`fila-agencia-N.mortas`) para créditos que
  não puderam ser aplicados, com reprocessamento via API.

```
                         RabbitMQ (CloudAMQP)
                 exchange topic "iceibank.eventos"
     publica         |  agencia.0.creditar -> fila-agencia-0 --x--> fila-agencia-0.mortas
  agencia.1.creditar |  agencia.1.creditar -> fila-agencia-1 --x--> fila-agencia-1.mortas
  Agencia 0 -------->|  agencia.2.creditar -> fila-agencia-2 --x--> fila-agencia-2.mortas
                                                  |        (x = rejeitada: dead-letter)
                                                  v consome
                                             Agencia 1
```

## Estrutura

```
iceibank/
├── agencia/                      serviço de agência (o mesmo código roda 3 vezes)
│   ├── pom.xml
│   ├── data/                     logs .jsonl e saída de console (fora do Git)
│   └── src/main/java/br/com/pucminas/iceibank/
│       ├── IceibankApplication.java      porta a partir de AGENCIA_ID; exige RABBITMQ_URL
│       ├── config/               partição, CORS, propriedades, MensageriaConfig (exchange/filas/DLQ)
│       ├── controller/           contas, transferências, mensagens mortas, auth, status
│       ├── service/              RelogioVetorial, RegistroEventos, Mensageria (publish),
│       │                         ConsumidorCreditos (subscribe), regras de conta/transferência
│       ├── security/             emissão e validação de JWT, filtro, usuários
│       ├── model/ dto/ exception/
│       └── tools/                MesclarLogs (linha do tempo causal) e GerarTokenExpirado
├── frontend/                     HTML/CSS/JS puro, sem build
├── app_mobile/                   app Flutter (Sprint 3) - ver app_mobile/README.md
├── e2e/                          testes Playwright gravados (sprint2.spec.js, sprint3.spec.js)
├── docs/                         roteiro do Sprint 3 e SPRINT3-ESTADO.md (estado e decisões)
├── scripts/                      subir/parar agências, demos e geração de prints
├── evidencias/sprint1/ 2/ 3/     prints pedidos nos roteiros (+ as saídas em texto e vídeos)
├── RESPOSTAS.md
└── README.md
```

## Pré-requisitos

- **JDK 25** (o `pom.xml` define `java.version = 25`). Os scripts em `scripts/` localizam
  sozinhos um JDK 25+ instalado; para rodar comandos Maven a mão, exporte o `JAVA_HOME`.
- **Um RabbitMQ.** O roteiro usa o **CloudAMQP** (plano gratuito *Little Lemur*): crie a
  instância, copie a **AMQP URL** e exporte-a em todo terminal que for subir uma agência:
  ```bash
  export RABBITMQ_URL="amqps://usuario:senha@host.cloudamqp.com/vhost"     # Git Bash
  $env:RABBITMQ_URL="amqps://usuario:senha@host.cloudamqp.com/vhost"       # PowerShell
  ```
  A agência se recusa a subir sem essa variável. A URL contém a senha: **nunca** a coloque em
  arquivo versionado. (Alternativa local: `docker run -d -p 5672:5672 -p 15672:15672
  rabbitmq:3-management` e `RABBITMQ_URL=amqp://localhost`.)
- `jq` e `curl` (usados pelos scripts de demonstração, em Git Bash no Windows).
- Nenhum banco de dados: as contas vivem em memória (o que a Parte C explora de propósito).

## Como executar

### 1. Subir as 3 agências

```bash
./scripts/subir-agencias.sh                 # compila se necessário e sobe as 3
./scripts/subir-agencias.sh --limpar-logs   # idem, apagando os .jsonl antes
./scripts/parar-agencias.sh                 # derruba as 3
./scripts/parar-agencias.sh 1               # derruba só a agência 1 (teste de resiliência)
```

Cada agência declara, ao conectar, a exchange, as 3 filas e as 3 filas de mortas (é
idempotente). A porta é `4000 + OFFSET + AGENCIA_ID`:

| agência | contas | porta | fila |
|---|---|---|---|
| 0 | 0, 3, 6, 9, … | 4000 | `fila-agencia-0` |
| 1 | 1, 4, 7, 10, … | 4001 | `fila-agencia-1` |
| 2 | 2, 5, 8, 11, … | 4002 | `fila-agencia-2` |

Para subir uma agência a mão (equivalente aos 3 terminais do roteiro), com `RABBITMQ_URL`
definida no terminal:

```bash
cd agencia
AGENCIA_ID=0 ./mvnw spring-boot:run     # e AGENCIA_ID=1, AGENCIA_ID=2 em outros terminais
```

> **OFFSET pessoal.** Em máquina compartilhada, defina os dois últimos dígitos da matrícula em
> `agencia/src/main/resources/application.yaml` (`iceibank.offset` ou a variável `OFFSET`),
> `frontend/js/modelo.js` (constante `OFFSET`) e `OFFSET=NN ./scripts/subir-agencias.sh`.

### 2. Abrir o frontend

```bash
./scripts/servir-frontend.sh        # http://localhost:5500 (ou abra frontend/index.html direto)
```

Usuários de laboratório: `ana/ana123`, `bruno/bruno123`, `carla/carla123`.

### 3. Rodar as demonstrações do Sprint 2

Cada parte imprime a data/hora da execução e corresponde a uma evidência:

```bash
./scripts/demo.sh preparar         # cria as contas de exemplo nas 3 agências
./scripts/demo.sh concorrentes     # um depósito em cada agência, em paralelo (Parte D)
./scripts/demo.sh local            # transferência dentro da mesma agência (não usa o RabbitMQ)
./scripts/demo.sh entre-agencias   # transferência assíncrona via RabbitMQ (Parte C)
./scripts/demo.sh resiliencia      # derruba a agência 1, transfere, religa (Parte C)
./scripts/demo.sh mortas           # dead-letter: recria a conta e reprocessa (funcionalidade adicional)
./scripts/demo.sh linha-do-tempo   # pares concorrentes x causais (Parte D)
./scripts/demo.sh auth             # regressão do JWT do Sprint 1
./scripts/demo.sh tudo             # todas, em ordem
```

Para transformar a saída em print (Windows, sem dependências):

```bash
./scripts/demo.sh resiliencia > saida.txt
powershell -ExecutionPolicy Bypass -File scripts/gerar-prints.ps1 saida.txt resiliencia-fila.png "Titulo"
```

### 4. Linha do tempo causal (Parte D)

```bash
cd agencia && ./mvnw -q exec:java@mesclar-logs
```

Lê todos os `data/eventos-agencia-*.jsonl`, ordena por hora de parede, lista os pares de
eventos de agências diferentes cujos vetores são **concorrentes** e confere, para cada
transferência entre agências (casada pelo `idTransferencia`), que débito/envio → crédito saem
como `ANTES`.

### 5. Sprint 3 — demonstrações em PowerShell (sem jq/curl/Python)

```powershell
$env:JAVA_HOME = "C:\caminho\do\jdk-25"   # ou o script acha %LOCALAPPDATA%\AndroidDev\jdk-25*
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 eleicao      # sobe as 3 juntas (Parte A)
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 escalonada   # sobe 0, 1, 2 com 4 s de intervalo
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 failover     # derruba a líder (Parte B)
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 historico    # HistoricoLideranca (Parte C)
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 conflito     # 409 da sincronização (Parte E)
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 parar        # derruba todas
```

`-Saida evidencias\sprint3\saidas\X.txt` grava a saída (com `Get-Date`) e gera o PNG em
`evidencias\sprint3\`. O histórico também roda direto: `cd agencia && ./mvnw -q exec:java@historico-lideranca`.

### 6. App Flutter

```powershell
cd app_mobile
flutter pub get
flutter run -d chrome                                  # ou um emulador Android (usa 10.0.2.2)
flutter build web --release --no-web-resources-cdn     # build usado pelos testes e2e
```

`e2e\rodar-sprint3.ps1` sobe as agências, serve o build web e roda os 8 testes gravados do
Sprint 3, que geram os prints do app em `evidencias/sprint3/` e o vídeo
`evidencias/sprint3/videos/sprint3-app.webm`.

### 7. Testes

```bash
cd agencia && ./mvnw test        # 61 testes (Bully com rede em memória, sincronização, Sprints 1-2)
cd app_mobile && flutter test    # 10 testes da lógica offline do app (agência simulada)
```

## API

Todas as rotas exigem `Authorization: Bearer <token>`, **exceto** `POST /auth/login`,
`GET /status` e as rotas `/bully/*` (chamadas entre agências e pelo painel do app).

| método | rota | descrição |
|---|---|---|
| `POST` | `/auth/login` | recebe `{usuario, senha}`, devolve o JWT (validade 30 min) |
| `GET` | `/status` | *(pública)* identidade, **relógio vetorial**, contas e mensagens em cada fila |
| `POST` | `/contas` | cria conta; recusa se `id % 3` não for esta agência |
| `GET` | `/contas` | contas do usuário autenticado nesta agência (com `vetorAtual`) |
| `GET` | `/contas/{id}` | consulta saldo (só o dono); *Sprint 3:* inclui `vetorAtual` |
| `POST` | `/contas/{id}/depositar` | `{valor}` |
| `POST` | `/contas/{id}/sacar` | `{valor}` |
| `GET` | `/contas/{id}/historico` | eventos da conta, com o vetor de cada um |
| `POST` | `/transferencias` | `{idOrigem, idDestino, valor}`; local na hora, entre agências via RabbitMQ |
| `POST` | `/mensagens-mortas/reprocessar` | *(Sprint 2, adicional)* devolve à fila os créditos mortos desta agência |
| `POST` | `/contas/{id}/sincronizar` | *(Sprint 3)* `{idOperacao, tipo, valor, vetorConhecido, idDestino?}` (tipo `DEPOSITO`, `SAQUE` ou `TRANSFERENCIA`); **409** `{erro, relacao, vetorConhecido, vetorAtual, contaAtual}` se o vetor mudou |
| `POST` | `/bully/eleicao` | *(Sprint 3, pública)* `{origemId, vetorEnvio}`; o 200 é o "OK" do Bully |
| `POST` | `/bully/coordenador` | *(Sprint 3, pública)* `{liderId, vetorEnvio}` |
| `GET` | `/bully/lider` | *(Sprint 3, pública)* `{idAgencia, liderAtual, eleicaoEmAndamento, horaParede}` |

Erros seguem sempre o formato `{"erro": "mensagem"}`. Uma transferência entre agências que
responde **200** significa "o broker confirmou a mensagem", **não** "o crédito já foi
aplicado". Se o RabbitMQ estiver indisponível, a resposta é **503** e o débito é estornado.

## Limitações conhecidas (propositais)

- **Sem atomicidade entre débito e crédito.** O débito é imediato; o crédito é assíncrono e
  pode falhar de vez (ex.: a agência de destino reiniciou e perdeu a conta). A mensagem não se
  perde — vai para a fila de mortas — mas a origem não é avisada nem estornada. Compensação
  automática é assunto do Sprint 4 (Saga).
- **Entrega pelo menos uma vez:** se a agência cair entre aplicar o crédito e confirmar a
  mensagem, ela será reentregue (o consumidor ainda não é idempotente pelo `idTransferencia`).
- **Contas em memória:** reiniciar uma agência apaga as contas dela. O log de eventos (e,
  com ele, o relógio vetorial) sobrevive em disco.
- **O consumidor não usa JWT:** quem tiver a AMQP URL pode publicar créditos. Ver
  `RESPOSTAS.md`, Parte C, pergunta 3.
- **Sprint 3 — líder só informativa:** nenhuma operação passa pela líder ainda (Sprint 4).
  As rotas `/bully/*` não têm autenticação (Parte A, pergunta 3).
- **Sprint 3 — conflito por agência, não por conta:** qualquer evento da agência (inclusive
  uma eleição) faz uma sincronização offline pendente dar 409 (Parte E, pergunta 2).

## Evidências

`evidencias/sprint3/` traz os 8 prints pedidos no roteiro do Sprint 3 (`eleicao-inicial`,
`failover`, `historico-lideranca`, `app-lider-atual`, `app-failover`,
`conflito-sincronizacao`, `app-offline`, `app-sincronizacao`), mais `app-conflito`,
`app-fluxo-bancario`, `funcionalidade-adicional`, `regressao-frontend-jwt`,
`eleicao-escalonada`, `historico-escalonada` e o vídeo `videos/sprint3-app.webm`. Os prints
do app combinam a tela (build web num Chromium com viewport de celular) com a saída real de
comandos PowerShell (`Get-Date`, `Invoke-RestMethod`) executados no mesmo instante.


`evidencias/sprint2/` traz os prints do Sprint 2 — `transferencia-assincrona.png`,
`resiliencia-fila.png`, `linha-do-tempo-causal.png`, `funcionalidade-adicional.png`, além de
`regressao-jwt.png`, `frontend-transferencia-assincrona.png` e os prints de preparação — com a
data/hora da execução visível. As saídas de texto correspondentes ficam em
`evidencias/sprint2/saidas/`. Os prints de terminal foram gerados a partir das saídas reais
com `scripts/gerar-prints.ps1`; o do frontend é uma captura do navegador.
`evidencias/sprint1/` continua com as evidências do Sprint 1.
