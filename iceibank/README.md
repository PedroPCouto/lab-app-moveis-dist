# ICEIBank

Banco simplificado dividido em **agências**, usado como projeto único ao longo dos 4 sprints
da disciplina. Cada agência é uma partição independente de contas — a conta `N` pertence à
agência `N % 3`, e nenhuma agência sabe o saldo das contas das outras.

| Sprint | Unidade | Tecnologia | Conceito de SD | Situação |
|---|---|---|---|---|
| **1** | U2 — Desenvolvimento Web | API REST / MVC | Relógio lógico de Lamport | **este** |
| 2 | U3 — Comunicação indireta | Mensageria / Pub-Sub | Relógio vetorial | — |
| 3 | U4 — Desenvolvimento Móvel | App Flutter | Consenso (eleição de líder) | — |
| 4 | U5 — Computação em Nuvem | Containers | Transações distribuídas (2PC/Saga) | — |

Linguagem escolhida para os 4 sprints: **Java 25 + Spring Boot 4.1**.
As respostas às perguntas do roteiro estão em [`RESPOSTAS.md`](RESPOSTAS.md).

---

## Estrutura

```
iceibank/
├── agencia/                      serviço de agência (o mesmo código roda 3 vezes)
│   ├── pom.xml
│   ├── data/                     logs .jsonl gerados em tempo de execução (fora do Git)
│   └── src/main/java/br/com/pucminas/iceibank/
│       ├── IceibankApplication.java      calcula a porta a partir de AGENCIA_ID
│       ├── config/               partição de contas, CORS, cliente HTTP, propriedades
│       ├── controller/           Parte C e D: contas, transferências, auth, status
│       ├── service/              Lamport, registro de eventos, regras de conta/transferência
│       ├── security/             Parte F: emissão e validação de JWT, filtro, usuários
│       ├── model/ dto/ exception/
│       └── tools/                MesclarLogs (Parte E) e GerarTokenExpirado (Parte F)
├── frontend/                     Parte G: HTML/CSS/JS puro, sem build
│   ├── index.html
│   ├── css/estilo.css
│   └── js/modelo.js  visao.js  controlador.js      (M, V e C separados)
├── scripts/                      subir/parar agências, demos e geração de prints
├── evidencias/sprint1/           prints pedidos na seção 4.2 (+ as saídas em texto)
├── RESPOSTAS.md
└── README.md
```

## Pré-requisitos

- **JDK 25** (o `pom.xml` define `java.version = 25`). Os scripts em `scripts/` localizam
  sozinhos um JDK 25+ instalado e ajustam o `JAVA_HOME`; para rodar comandos Maven a mão,
  exporte-o você mesmo.
- `jq` (usado pelos scripts de demonstração) e `python3` (para servir o frontend).
- Nenhum banco de dados: as contas vivem em memória, por decisão do roteiro (seção 7.2).

## Como executar

### 1. Subir as 3 agências

```bash
./scripts/subir-agencias.sh          # compila se necessário e sobe as 3
./scripts/parar-agencias.sh          # derruba as 3
./scripts/parar-agencias.sh 1        # derruba só a agência 1 (para a falha da Parte D)
```

As 3 agências são o **mesmo código** com identidades diferentes; a porta é derivada da
identidade (`4000 + OFFSET + AGENCIA_ID`):

| agência | contas | porta |
|---|---|---|
| 0 | 0, 3, 6, 9, … | 4000 |
| 1 | 1, 4, 7, 10, … | 4001 |
| 2 | 2, 5, 8, 11, … | 4002 |

Para subir uma agência a mão (equivalente às 3 janelas do roteiro):

```bash
cd agencia
AGENCIA_ID=0 ./mvnw spring-boot:run     # e AGENCIA_ID=1, AGENCIA_ID=2 em outros terminais
```

> **OFFSET pessoal.** Se for rodar em máquina compartilhada do laboratório, defina os dois
> últimos dígitos da matrícula/RA em três lugares que precisam concordar:
> `agencia/src/main/resources/application.yaml` (`iceibank.offset`, ou a variável de ambiente
> `OFFSET`), `frontend/js/modelo.js` (constante `OFFSET`) e, se for usar os scripts,
> `OFFSET=NN ./scripts/subir-agencias.sh`.

### 2. Abrir o frontend

```bash
./scripts/servir-frontend.sh        # http://localhost:5500
```

Abrir `frontend/index.html` direto pelo navegador também funciona (o backend libera CORS).
Usuários de laboratório: `ana/ana123`, `bruno/bruno123`, `carla/carla123`.

### 3. Rodar as demonstrações

Cada parte imprime a data/hora da execução e corresponde a uma evidência da seção 4.2:

```bash
./scripts/demo.sh preparar             # cria as contas de exemplo nas 3 agências
./scripts/demo.sh local                # transferência dentro da mesma agência
./scripts/demo.sh entre-agencias       # transferência entre agências (regras 2 e 3 do Lamport)
./scripts/demo.sh falha                # derruba a agência de destino: inconsistência conhecida
./scripts/demo.sh linha-do-tempo       # mescla os logs das 3 agências (Parte E)
./scripts/demo.sh auth                 # os três cenários de token da Parte F
./scripts/demo.sh extra                # histórico por conta + status por agência
./scripts/demo.sh tudo                 # todas, em ordem
```

### 4. Linha do tempo unificada (Parte E)

```bash
cd agencia && ./mvnw -q exec:java@mesclar-logs
```

Lê todos os `data/eventos-agencia-*.jsonl`, ordena por relógio de Lamport e marca com
`<-- EMPATE` os eventos de agências diferentes que receberam o mesmo timestamp — os candidatos
a concorrentes.

### 5. Testes

```bash
cd agencia && ./mvnw test        # 29 testes
```

## API

Todas as rotas exigem `Authorization: Bearer <token>`, **exceto** `POST /auth/login` e
`GET /status`.

| método | rota | descrição |
|---|---|---|
| `POST` | `/auth/login` | recebe `{usuario, senha}`, devolve o JWT (validade 30 min) |
| `GET` | `/status` | *(pública)* identidade, relógio de Lamport e contas da partição |
| `POST` | `/contas` | cria conta; recusa se `id % 3` não for esta agência |
| `GET` | `/contas` | contas do usuário autenticado nesta agência |
| `GET` | `/contas/{id}` | consulta saldo (só o dono) |
| `POST` | `/contas/{id}/depositar` | `{valor}` |
| `POST` | `/contas/{id}/sacar` | `{valor}` |
| `GET` | `/contas/{id}/historico` | *(funcionalidade adicional)* eventos da conta |
| `POST` | `/transferencias` | `{idOrigem, idDestino, valor}`; decide sozinha local × entre agências |
| `POST` | `/contas/{id}/creditar-remoto` | **interna**: só aceita token de tipo `SISTEMA` |

Erros seguem sempre o formato `{"erro": "mensagem"}`.

## Limitação conhecida (proposital)

Se uma transferência **entre agências** falhar depois do débito — agência de destino fora do
ar, rede caindo —, o débito **não é revertido**. O dinheiro some temporariamente, a API
responde `502` e a agência registra um evento `TRANSFERENCIA_FALHOU` no log com o campo
`inconsistencia`. Isso é intencional neste sprint: é exatamente o problema que o Sprint 4
resolve com uma transação distribuída de verdade (2PC ou Saga). Ver `RESPOSTAS.md`, Parte D.

## Evidências

`evidencias/sprint1/` traz os prints pedidos na seção 4.2 (e os das seções 2.1, 11.2 e 12.2),
com a data/hora da execução visível. As saídas de texto correspondentes ficam em
`evidencias/sprint1/saidas/`, para conferência do conteúdo sem depender da imagem. Os prints
de terminal foram gerados a partir das saídas reais com `scripts/gerar-prints.py`; os do
frontend são capturas do navegador.

## Sequência de commits sugerida

O repositório ainda **não** foi inicializado. O roteiro (seção 4.4) pede histórico incremental,
com pelo menos um commit por parte concluída, e um commit separado para a funcionalidade
adicional. Sugestão de sequência, respeitando os pontos de commit indicados nas seções 5 a 12:

```bash
git init
git add .gitignore .gitattributes README.md
git commit -m "chore: estrutura inicial do repositorio"

git add agencia/pom.xml agencia/mvnw agencia/mvnw.cmd agencia/.mvn agencia/HELP.md \
        agencia/src/main/java/br/com/pucminas/iceibank/IceibankApplication.java \
        agencia/src/main/java/br/com/pucminas/iceibank/config/
git commit -m "feat(config): define particionamento de contas entre 3 agencias"

git add agencia/src/main/java/br/com/pucminas/iceibank/service/RelogioLamport.java \
        agencia/src/main/java/br/com/pucminas/iceibank/service/RegistroEventos.java \
        agencia/src/main/java/br/com/pucminas/iceibank/model/Evento.java \
        agencia/src/test/java/br/com/pucminas/iceibank/RelogioLamportTest.java \
        agencia/src/test/java/br/com/pucminas/iceibank/ParticionamentoTest.java
git commit -m "feat(lamport): implementa relogio logico e registro de eventos"

git add agencia/src/main/java/br/com/pucminas/iceibank/controller/ContasController.java \
        agencia/src/main/java/br/com/pucminas/iceibank/service/ContaService.java \
        agencia/src/main/java/br/com/pucminas/iceibank/model/Conta.java \
        agencia/src/main/java/br/com/pucminas/iceibank/dto/ agencia/src/main/java/br/com/pucminas/iceibank/exception/ \
        agencia/src/main/resources/application.yaml
git commit -m "feat(contas): implementa API REST/MVC de contas com relogio de Lamport"

git add agencia/src/main/java/br/com/pucminas/iceibank/controller/TransferenciasController.java \
        agencia/src/main/java/br/com/pucminas/iceibank/service/TransferenciaService.java \
        agencia/src/main/java/br/com/pucminas/iceibank/config/RestClientConfig.java \
        evidencias/sprint1/transferencia-local.png \
        evidencias/sprint1/transferencia-entre-agencias.png \
        evidencias/sprint1/falha-conhecida.png
git commit -m "feat(transferencias): implementa transferencia local e entre agencias"

git add agencia/src/main/java/br/com/pucminas/iceibank/tools/MesclarLogs.java \
        scripts/ evidencias/sprint1/linha-do-tempo.png RESPOSTAS.md
git commit -m "feat(observabilidade): adiciona script de linha do tempo unificada"

git add agencia/src/main/java/br/com/pucminas/iceibank/security/ \
        agencia/src/main/java/br/com/pucminas/iceibank/tools/GerarTokenExpirado.java \
        agencia/src/test/java/br/com/pucminas/iceibank/JwtServiceTest.java \
        agencia/src/test/java/br/com/pucminas/iceibank/ApiIntegracaoTest.java \
        evidencias/sprint1/auth-*.png RESPOSTAS.md
git commit -m "feat(auth): protege a API com autenticacao JWT"

git add frontend agencia/src/main/java/br/com/pucminas/iceibank/config/WebConfig.java \
        evidencias/sprint1/frontend-*.png
git commit -m "feat(frontend): implementa interface web para o ICEIBank"

# commit proprio da funcionalidade adicional (secao 2.1)
git add agencia/src/main/java/br/com/pucminas/iceibank/controller/StatusController.java \
        agencia/src/main/java/br/com/pucminas/iceibank/dto/StatusResponse.java \
        agencia/src/main/java/br/com/pucminas/iceibank/dto/EventoResponse.java \
        evidencias/sprint1/funcionalidade-adicional.png RESPOSTAS.md
git commit -m "feat(extra): historico de eventos por conta e health-check por agencia"

git add .
git commit -m "docs: respostas do sprint 1 e evidencias restantes"
```

> Como o projeto foi construído de uma vez, esses commits sairão todos com a mesma data. Se o
> histórico ao longo das semanas for critério de avaliação, vale ir commitando por parte à
> medida que revisar cada uma, em vez de rodar a sequência inteira de uma vez.
