# Comunicação indireta: Mensageria, Pub/Sub e Relógio Vetorial

## Atividade de recapitulação do sistema desenvolvido

### 1. Arquitetura atual e divisão em agências

Como a aplicação está distribuída atualmente? Identifique os processos,
serviços ou componentes envolvidos, as agências existentes e a
responsabilidade de cada um. Explique como uma conta é associada a uma
agência e quais operações são realizadas localmente ou dependem de outra
agência.

**Direcionamento:** identificar os participantes e as fronteiras de
comunicação que serão utilizadas na próxima implementação.

**Resposta:**

- **Frontend** (`frontend/`): HTML/JS estático. Faz login e chama a agência
  dona da conta.
- **3 agências** (`agencia/`): o mesmo JAR Spring Boot rodando 3 vezes, uma por
  processo, nas portas `4000 + OFFSET + id` (0, 1 e 2). Cada uma guarda suas
  contas em memória (`ContaService`), tem seu relógio de Lamport
  (`RelogioLamport`) e grava seus eventos em `data/eventos-agencia-N.jsonl`
  (`RegistroEventos`).
- **MesclarLogs**: ferramenta offline que junta os logs das agências e ordena
  por Lamport.

A conta é associada pela regra `agencia = idConta mod 3`
(`AgenciaConfig.agenciaResponsavel`). Ex.: conta 0 → agência 0, conta 1 →
agência 1.

- **Locais:** criar conta, depósito, saque, histórico, transferência entre
  contas da mesma agência.
- **Dependem de outra agência:** transferência cujo destino é de outra agência
  (crédito remoto).

```
Frontend ──HTTP──> Agência 0 :4000 ──HTTP──> Agência 1 :4001
                        └───────────HTTP──> Agência 2 :4002
```

### 2. Comunicação atual entre as agências

Como uma agência se comunica atualmente com outra agência ou serviço?
Descreva o mecanismo utilizado, o fluxo de uma requisição e as respostas
esperadas. O que acontece quando o componente destinatário está
indisponível, demora para responder ou não recebe a mensagem?

**Direcionamento:** reconhecer as limitações da comunicação direta e os
motivos para introduzir comunicação indireta.

**Resposta:**

É comunicação **direta e síncrona** por REST (`RestClient`). A agência de
origem faz:

```
POST http://localhost:4001/contas/1/creditar-remoto
Authorization: Bearer <token interno JWT, 60s>
{ "valor": 10, "timestampLamport": 9, "origemAgencia": 0 }
```

Resposta esperada: `200` com `{"mensagem":"Credito remoto aplicado.","saldoAtual":...}`.
A origem espera essa resposta antes de responder ao cliente.

Problemas:

- **Destino fora do ar:** `Connection refused`. O débito já foi feito, não é
  desfeito, e o cliente recebe `502` (evidência `falha-conhecida.txt`: saldo
  foi de 53 para 43 sem crédito no destino).
- **Destino lento:** timeout de 3s (conexão) / 5s (leitura). Pior caso: o
  destino **creditou**, mas a resposta não chegou; a origem registra falha
  mesmo assim.
- **Mensagem perdida:** não há reenvio nem fila. A operação simplesmente se perde.

Ou seja: origem e destino precisam estar vivos ao mesmo tempo (acoplamento
temporal) e a origem precisa saber o endereço do destino (acoplamento espacial).

### 3. Operações distribuídas e seus efeitos

Escolha uma operação do banco que envolva mais de uma agência, como uma
transferência entre contas de agências distintas. Descreva passo a passo
quais componentes participam, quais dados são alterados e em que
momentos a operação é considerada concluída.

**Direcionamento:** identificar os eventos distribuídos que precisarão
ser comunicados por mensagens.

**Resposta:** transferência de R$ 10 da conta 0 (agência 0) para a conta 1
(agência 1), em `TransferenciaService.transferir`:

1. Frontend → Agência 0: `POST /transferencias`.
2. Agência 0 valida dono da conta e calcula `1 mod 3 = 1` → outra agência.
3. Agência 0 verifica saldo, **debita** a conta 0 e registra
   `TRANSFERENCIA_DEBITO` (Lamport `eventoLocal`).
4. Agência 0 incrementa o relógio (`aoEnviar`) e chama
   `/contas/1/creditar-remoto` na Agência 1.
5. Agência 1 faz `max(local, recebido) + 1`, **credita** a conta 1 e registra
   `TRANSFERENCIA_CREDITO_REMOTO`.
6. Agência 1 responde 200 → Agência 0 responde 200 ao frontend.

Dados alterados: saldo da conta 0 (passo 3), saldo da conta 1 (passo 5) e os
logs das duas agências.

A operação só é **concluída** no passo 6. Entre os passos 3 e 5 o dinheiro
"não está em lugar nenhum". Se o passo 4 falhar, fica registrado
`TRANSFERENCIA_FALHOU` e a inconsistência permanece.

Eventos distribuídos identificados: débito na origem, crédito no destino,
falha.

### 4. Eventos que precisam ser comunicados

Quais eventos relevantes do sistema atual poderiam ser publicados para
que outras partes da aplicação fossem notificadas sem uma chamada
direta? Escolha pelo menos dois eventos, descreva seus dados e indique
quais componentes poderiam produzi-los e consumi-los.

**Direcionamento:** preparar a identificação de tópicos, mensagens e
participantes do modelo Pub/Sub.

**Resposta:**

| Evento | Dados | Produtor | Consumidores |
|---|---|---|---|
| `TransferenciaSolicitada` (débito feito) | idMensagem, idTransferencia, idOrigem, idDestino, valor, agenciaOrigem, agenciaDestino, relógio | Agência de origem | Agência de destino (credita), serviço de auditoria/log |
| `CreditoAplicado` | idMensagem, idTransferencia, idDestino, valor, novoSaldo, relógio | Agência de destino | Agência de origem (marca como concluída), frontend/notificação, auditoria |
| `CreditoRecusado` (ex.: conta não existe) | idTransferencia, motivo, relógio | Agência de destino | Agência de origem (estorna o débito) |
| `ContaCriada` / `Deposito` / `Saque` | id, valor, novoSaldo, relógio | Agência dona da conta | Auditoria, histórico, painel de monitoramento |

Os dados de detalhes já existem hoje em `RegistroEventos.registrar(...)`;
bastaria publicá-los além de gravá-los no `.jsonl`.

### 5. Mensageria e comunicação indireta

Considerando uma transferência entre agências, como o fluxo poderia ser
implementado utilizando um sistema de mensageria ou Pub/Sub, em vez de
uma comunicação direta entre os serviços? Identifique o produtor, o
canal ou tópico, os consumidores e o conteúdo das mensagens.

**Direcionamento:** projetar a primeira versão do fluxo de comunicação
indireta.

**Resposta:** usando um broker (ex.: RabbitMQ ou Kafka):

```
Agência 0 ──publica──> [transferencias.agencia-1] ──consome──> Agência 1
    ▲                                                              │
    └───consome─── [transferencias.resultado.agencia-0] <──publica─┘
```

- **Produtor:** Agência 0 debita, grava a transferência como `PENDENTE` e
  publica `TransferenciaSolicitada` no tópico `transferencias.agencia-1`.
  Responde ao cliente `202 Accepted` (sem esperar o destino).
- **Canal:** um tópico/fila por agência de destino
  (`transferencias.agencia-{id}`) e um de resultado por agência de origem.
- **Consumidor:** Agência 1 consome, credita e publica `CreditoAplicado` (ou
  `CreditoRecusado`) em `transferencias.resultado.agencia-0`.
- Agência 0 consome o resultado e marca `CONCLUIDA` (ou estorna).

Mensagem:

```json
{
  "idMensagem": "b7c1-...",
  "idTransferencia": "t-0-42",
  "tipo": "TransferenciaSolicitada",
  "idOrigem": 0, "idDestino": 1, "valor": 10.00,
  "agenciaOrigem": 0, "agenciaDestino": 1,
  "relogioVetorial": [9, 4, 2]
}
```

Se a Agência 1 estiver fora, a mensagem espera no broker e é entregue quando
ela voltar.

### 6. Entrega, duplicidade e processamento de mensagens

Se uma mensagem de transferência for entregue mais de uma vez, ou se for
recebida fora da ordem esperada, quais problemas poderiam ocorrer no
sistema atual? Analise pelo menos um cenário de duplicidade e um cenário
de falha ou reprocessamento. Que informações seriam necessárias para
processar uma mensagem com segurança?

**Direcionamento:** antecipar idempotência, identificação de mensagens e
tratamento de falhas na mensageria.

**Resposta:**

- **Duplicidade:** o `creditarRemoto` atual só faz `conta.creditar(valor)`,
  sem nenhum identificador. Se a mesma mensagem chegar 2 vezes (entrega
  "at-least-once"), a conta 1 recebe R$ 20 em vez de R$ 10.
- **Falha / reprocessamento:** a Agência 1 credita e cai antes de confirmar
  (`ack`) a mensagem. O broker reentrega → crédito em dobro. No inverso, se ela
  confirmar antes de creditar e cair, o crédito se perde.
- **Fora de ordem:** chega `Estorno` antes da `TransferenciaSolicitada`, ou um
  saque processado antes do crédito que lhe daria saldo → saldo insuficiente
  indevido.

Informações necessárias:

- `idMensagem` / `idTransferencia` único → tabela de "já processadas"
  (idempotência).
- Estado da transferência (`PENDENTE`, `CONCLUIDA`, `ESTORNADA`).
- Agência de origem + relógio (vetorial) para ordenar e detectar causalidade.
- Confirmar (`ack`) só depois de aplicar e registrar; fila de mensagens mortas
  (DLQ) para as que falham sempre.

### 7. Eventos concorrentes e ordenação causal

Considere duas agências executando operações simultaneamente, com troca
de mensagens entre elas. Apresente um cenário em que a ordem de
recebimento das mensagens seja diferente da ordem em que os eventos
ocorreram. Quais consequências essa situação pode trazer para a
interpretação dos eventos do banco?

**Direcionamento:** identificar a necessidade de distinguir ordem local,
ordem de recebimento e relação causal.

**Resposta:** a auditoria (ou uma terceira agência) recebe eventos das
agências 0 e 1:

1. Agência 0 publica `m1`: transferência de R$ 50 da conta 0 → conta 1.
2. Agência 1 consome `m1` e credita a conta 1.
3. Agência 1 publica `m2`: transferência de R$ 50 da conta 1 → conta 2
   (só possível por causa do crédito de `m1`).
4. Por atraso na rede/broker, a auditoria recebe **`m2` antes de `m1`**.

Consequências:

- A auditoria vê a conta 1 gastando dinheiro que "ainda não tinha" → parece
  saldo negativo ou fraude.
- Um histórico ordenado por recebimento fica errado.
- Ao mesmo tempo, um depósito na conta 3 (agência 0) e um saque na conta 4
  (agência 1) sem troca de mensagens são **concorrentes**: qualquer ordem é
  válida.

O Lamport atual não resolve: ele garante `a → b ⇒ L(a) < L(b)`, mas
`L(a) < L(b)` **não** prova que `a` causou `b`. O `MesclarLogs` inclusive
marca "EMPATE" quando não consegue ordenar.

### 8. Relógio vetorial na aplicação

Como o relógio vetorial poderia ser associado aos eventos e às mensagens
do sistema distribuído? Defina quais processos ou agências participariam
do vetor, em quais momentos ele seria atualizado e como seria utilizado
para comparar dois eventos. Utilize um exemplo de dois eventos
concorrentes e dois eventos causalmente relacionados.

**Direcionamento:** transformar o conceito teórico de relógio vetorial
em um mecanismo integrado à aplicação.

**Resposta:**

- **Participantes:** as 3 agências → vetor `V = [a0, a1, a2]`. Cada agência
  começa com `[0, 0, 0]`. Substituiria `RelogioLamport` por um
  `RelogioVetorial`.
- **Atualização** (agência `i`):
  - evento local (depósito, saque, débito): `V[i]++`;
  - envio/publicação: `V[i]++` e o vetor vai na mensagem;
  - recebimento: `V[k] = max(V[k], Vmsg[k])` para todo `k`, depois `V[i]++`.
- **Comparação:**
  - `A → B` (A causou B) se `A[k] ≤ B[k]` para todo `k` e `A ≠ B`;
  - **concorrentes** (`A ∥ B`) se nem `A ≤ B` nem `B ≤ A`.

Exemplo:

| Evento | Agência | Vetor |
|---|---|---|
| E1: depósito na conta 3 | 0 | `[1, 0, 0]` |
| E2: saque na conta 4 | 1 | `[0, 1, 0]` |
| E3: débito da conta 0 + publica transferência | 0 | `[2, 0, 0]` |
| E4: crédito na conta 1 (recebe E3) | 1 | `[2, 2, 0]` |

- **Concorrentes:** E1 `[1,0,0]` e E2 `[0,1,0]` → `1 > 0` numa posição e
  `0 < 1` na outra → `E1 ∥ E2`.
- **Causais:** E3 `[2,0,0]` e E4 `[2,2,0]` → todas as posições `≤` →
  `E3 → E4`.

### 9. Consistência e observabilidade dos eventos

Como seria possível verificar, na aplicação, se uma mensagem foi
produzida antes ou depois de outra, se dois eventos são concorrentes e
se uma agência recebeu informações causadas por eventos de outra
agência? Identifique quais dados, logs ou metadados deveriam ser
registrados para permitir essa análise.

**Direcionamento:** preparar a instrumentação e os experimentos de
ordenação causal da próxima versão.

**Resposta:** estender o `Evento` gravado no `.jsonl`:

```json
{
  "agencia": "agencia-1",
  "tipo": "TRANSFERENCIA_CREDITO_REMOTO",
  "relogioVetorial": [2, 2, 0],
  "relogioRecebido": [2, 0, 0],
  "idMensagem": "b7c1-...",
  "idTransferencia": "t-0-42",
  "agenciaOrigem": 0,
  "topico": "transferencias.agencia-1",
  "horaParede": "2026-09-14T13:45:29Z",
  "horaRecebimento": "2026-09-14T13:45:31Z",
  "duplicada": false,
  "detalhes": { "idConta": 1, "valor": 10, "novoSaldo": 60 }
}
```

Com isso:

- **Antes/depois:** comparar `relogioVetorial` de dois eventos (regra da
  questão 8).
- **Concorrência:** vetores incomparáveis.
- **Recebeu causa de outra agência:** `relogioRecebido` + `idMensagem`
  ligam o evento de recebimento ao evento de envio no log da outra agência.
- **Ordem de recebimento vs. causal:** comparar `horaRecebimento` com a ordem
  pelos vetores.

Evoluir o `MesclarLogs` para, em vez de "EMPATE", imprimir `→` ou `∥` entre
eventos e sinalizar mensagens entregues fora da ordem causal ou duplicadas.

### 10. Proposta de evolução para a próxima implementação

Com base nos problemas identificados, proponha como o sistema poderia
evoluir para utilizar comunicação indireta com mensageria ou Pub/Sub e
relógios vetoriais. Descreva uma operação que será modificada, quais
componentes serão envolvidos, quais mensagens serão trocadas, quais
metadados serão adicionados e como será possível demonstrar que a nova
implementação funciona corretamente.

**Direcionamento:** consolidar a recapitulação em uma proposta concreta
de implementação e experimentação.

**Resposta:**

**Operação modificada:** transferência entre agências
(`TransferenciaService.creditarEmOutraAgencia`), trocando o `RestClient` por
publicação no broker.

**Componentes:**

- Broker RabbitMQ (container Docker, subido pelo `subir-agencias.sh`).
- 3 agências, cada uma produtora e consumidora.
- `RelogioVetorial` no lugar de `RelogioLamport`.
- `MesclarLogs` atualizado para ordenação causal.

**Mensagens:**

1. `TransferenciaSolicitada` — origem → `transferencias.agencia-{destino}`.
2. `CreditoAplicado` ou `CreditoRecusado` — destino →
   `transferencias.resultado.agencia-{origem}`.
3. Na origem: `CreditoAplicado` → `CONCLUIDA`; `CreditoRecusado` → estorno
   (`TRANSFERENCIA_ESTORNO`).

**Metadados:** `idMensagem`, `idTransferencia`, `agenciaOrigem`,
`relogioVetorial`, `estado`; no destino, um conjunto de `idTransferencia` já
processados.

**Demonstração (roteiro como o `demo.sh`):**

1. **Caminho feliz:** transferência 0 → 1; saldos corretos e vetores
   `[2,0,0] → [2,2,0]` no log.
2. **Destino fora do ar:** derrubar a agência 1, transferir (recebe `202`),
   subir a agência 1 → crédito aplicado depois. Compara com o
   `falha-conhecida.txt` atual, em que o dinheiro sumia.
3. **Duplicidade:** republicar a mesma mensagem manualmente → saldo não muda e
   o log mostra `duplicada: true`.
4. **Conta inexistente:** transferir para conta 7 não criada → estorno na
   origem.
5. **Concorrência:** operações simultâneas nas agências 0 e 1 → `MesclarLogs`
   mostra `∥` entre elas e `→` nas transferências.
6. **Testes:** unitários para `RelogioVetorial` (comparação `→`/`∥`) e para a
   idempotência do consumidor, no estilo de `RelogioLamportTest`.

## Sugestão de entrega

Para cada pergunta, deve ser respondido com base no código e na
execução do sistema que desenvolveram, apresentando diagramas, exemplos
de mensagens ou trechos de implementação quando forem úteis.
