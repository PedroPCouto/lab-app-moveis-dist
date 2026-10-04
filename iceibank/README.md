# ICEIBank

Banco simplificado dividido em **agências**, usado como projeto único ao longo dos 4 sprints
da disciplina. Cada agência é uma partição independente de contas — a conta `N` pertence à
agência `N % 3`, e nenhuma agência sabe o saldo das contas das outras.

| Sprint | Unidade | Tecnologia | Conceito de SD | Situação |
|---|---|---|---|---|
| 1 | U2 — Desenvolvimento Web | API REST / MVC | Relógio lógico de Lamport | concluído |
| **2** | U3 — Comunicação indireta | Mensageria / Pub-Sub (RabbitMQ) | Relógio vetorial | **este** |
| 3 | U4 — Desenvolvimento Móvel | App Flutter | Consenso (eleição de líder) | — |
| 4 | U5 — Computação em Nuvem | Containers | Transações distribuídas (2PC/Saga) | — |

Linguagem escolhida para os 4 sprints: **Java 25 + Spring Boot 4.1** (+ Spring AMQP no Sprint 2).
As respostas às perguntas dos roteiros estão em [`RESPOSTAS.md`](RESPOSTAS.md).

---

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
├── scripts/                      subir/parar agências, demos e geração de prints
├── evidencias/sprint1/ sprint2/  prints pedidos nos roteiros (+ as saídas em texto)
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

### 5. Testes

```bash
cd agencia && ./mvnw test        # 37 testes (os de serviço usam um mock no lugar do RabbitMQ)
```

## API

Todas as rotas exigem `Authorization: Bearer <token>`, **exceto** `POST /auth/login` e
`GET /status`.

| método | rota | descrição |
|---|---|---|
| `POST` | `/auth/login` | recebe `{usuario, senha}`, devolve o JWT (validade 30 min) |
| `GET` | `/status` | *(pública)* identidade, **relógio vetorial**, contas e mensagens em cada fila |
| `POST` | `/contas` | cria conta; recusa se `id % 3` não for esta agência |
| `GET` | `/contas` | contas do usuário autenticado nesta agência |
| `GET` | `/contas/{id}` | consulta saldo (só o dono) |
| `POST` | `/contas/{id}/depositar` | `{valor}` |
| `POST` | `/contas/{id}/sacar` | `{valor}` |
| `GET` | `/contas/{id}/historico` | eventos da conta, com o vetor de cada um |
| `POST` | `/transferencias` | `{idOrigem, idDestino, valor}`; local na hora, entre agências via RabbitMQ |
| `POST` | `/mensagens-mortas/reprocessar` | *(Sprint 2, adicional)* devolve à fila os créditos mortos desta agência |

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

## Evidências

`evidencias/sprint2/` traz os prints do Sprint 2 — `transferencia-assincrona.png`,
`resiliencia-fila.png`, `linha-do-tempo-causal.png`, `funcionalidade-adicional.png`, além de
`regressao-jwt.png`, `frontend-transferencia-assincrona.png` e os prints de preparação — com a
data/hora da execução visível. As saídas de texto correspondentes ficam em
`evidencias/sprint2/saidas/`. Os prints de terminal foram gerados a partir das saídas reais
com `scripts/gerar-prints.ps1`; o do frontend é uma captura do navegador.
`evidencias/sprint1/` continua com as evidências do Sprint 1.
