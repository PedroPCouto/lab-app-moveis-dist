# Sprint 3 — estado atual, decisões e como continuar

> Documento de passagem de bastão. Qualquer pessoa (ou modelo de IA) que pegar o projeto
> deve começar por aqui. Atualize-o a cada etapa concluída.
> **Última atualização: 15/11/2026 — Sprint 3 implementado e evidenciado.**

Roteiro original do Sprint 3: [`docs/roteiro-sprint3/`](roteiro-sprint3/) (cópia fiel dos
arquivos do professor). Checklist e critérios de avaliação em
`sprint3-iceibank-adaptacao-checklist-avaliacao.md`. Respostas às perguntas:
[`RESPOSTAS.md`](../RESPOSTAS.md), seção "Sprint 3".

## Checklist da seção 12

| Item | Situação | Onde |
|---|---|---|
| Parte A — eleição/coordenador (Bully) | feito | `agencia/.../service/BullyService.java`, `controller/BullyController.java`; `eleicao-inicial.png`, `eleicao-escalonada.png` |
| As 3 convergem para a mesma líder, mesmo com a tempestade | feito (verificado em execução real e em `BullyServiceTest`) | idem |
| Parte B — heartbeat + reeleição automática | feito | `BullyService.verificarLider/iniciarHeartbeat`; `failover.png` |
| Parte C — histórico de lideranças | feito, em Java (`tools/HistoricoLideranca.java`, `exec:java@historico-lideranca`) | `historico-lideranca.png`, `historico-escalonada.png` |
| App Flutter — fluxo bancário completo | feito | `app_mobile/lib/src/banco.dart`, `telas/`; `app-fluxo-bancario.png` |
| App Flutter — painel da líder + failover na UI | feito | `lideranca.dart`, `telas/painel_lider.dart`; `app-lider-atual.png`, `app-failover.png` |
| Parte E — `/contas/{id}/sincronizar` + 409 | feito (inclui TRANSFERENCIA e idempotência) | `SincronizacaoService`, `ContaService.aplicarSincronizado`; `conflito-sincronizacao.png` |
| Parte F — offline-first no app | feito | `banco.dart` (fila/sincronizar), `conectividade.dart`, `armazenamento.dart`; `app-offline.png`, `app-sincronizacao.png` |
| 409 visível no app | feito (diálogo + cartão, decisão da pessoa) | `telas/pendentes.dart`; `app-conflito.png` |
| Sem regressão (JWT, frontend web, mensageria/vetorial) | feito, inclusive mensageria real no CloudAMQP (suíte do Sprint 2, 3/3) | `regressao-frontend-jwt.png`; `videos/regressao-sprint2-*.webm`; testes Java |
| Funcionalidade adicional | feito: painel do cluster no app (agências ativas + histórico de trocas de líder) | `agencias.dart`, `telas/painel_cluster.dart`; `funcionalidade-adicional.png` |
| `evidencias/sprint3/` com os 8 prints | feito (+6 extras e vídeo `videos/sprint3-app.webm`) | |
| RESPOSTAS.md (5.5, 6.4, 7.3, 8.3, 9.6, 10.3 + adicional) | feito | `RESPOSTAS.md` |
| README atualizado | feito | `README.md`, `app_mobile/README.md` |

Testes: **61 Java** (`cd agencia && ./mvnw test`), **10 Dart** (`cd app_mobile && flutter
test`), **8 Playwright** (`e2e\rodar-sprint3.ps1`). Todos passando em 15/11/2026.

## Pendências que dependem do usuário

1. ~~Regressão da mensageria com RabbitMQ real~~ — **feita em 15/11/2026 18:47**: o usuário
   forneceu a URL do CloudAMQP, gravada em `iceibank/.env.local` (fora do Git, a pedido dele);
   `e2e\rodar.ps1` passou os 3 testes do Sprint 2 (vídeos em
   `evidencias/sprint3/videos/regressao-sprint2-teste-{a,b,c}.webm`). Única mudança no teste:
   a asserção `[N,0,0]` do vetor virou `[N,x,y]`, porque o Bully faz as agências trocarem
   mensagens desde a subida.
2. **Rodar o app num Android de verdade (opcional).** Não há emulador nem espaço em disco
   (~5 GB livres). O projeto `android/` está pronto (INTERNET, ACCESS_NETWORK_STATE,
   `usesCleartextTraffic`). Precisaria habilitar Android no Flutter
   (`flutter config --enable-android`), Android SDK com plataforma recente e um
   emulador/dispositivo. O host padrão no Android é `10.0.2.2`.
3. **Push** para o GitHub (`origin`): os commits estão só locais; não foi feito push.

## Ambiente desta máquina (Windows 11, disco C: quase cheio)

- **JDK 25** portátil em `%LOCALAPPDATA%\AndroidDev\jdk-25.0.5+8` (o `pom.xml` exige Java 25).
  Os scripts o encontram sozinhos; para Maven à mão, exporte `JAVA_HOME`.
- **Flutter 3.50.0 / Dart 3.14** em `%LOCALAPPDATA%\AndroidDev\flutter` (clone raso da tag
  3.50.0, só artefatos **web**). Android/iOS/desktop desabilitados no `flutter config`.
  Não está no PATH: use `"$env:LOCALAPPDATA\AndroidDev\flutter\bin\flutter"`.
- No Flutter 3.50 o Material saiu do SDK: o app importa `package:material_ui/material_ui.dart`.
- **RabbitMQ (CloudAMQP):** credenciais em `iceibank/.env.local` (URL, host, usuário, vhost,
  senha, portas), **fora do Git** (`.gitignore`; modelo versionado em `.env.exemplo`). Decisão
  do usuário: cenário educacional, não quer reconfigurar a cada sessão. Todos os scripts
  (`scripts/sprint3.ps1`, `scripts/comum.sh`, `e2e/rodar*.ps1`, `e2e/apoio/agencias.js`) leem o
  arquivo quando `RABBITMQ_URL` não está no ambiente. Sem o arquivo, as agências sobem com
  `amqp://localhost` (sem broker): só a transferência entre agências responde 503.
  Painel da instância: https://customer.cloudamqp.com/ (plano Little Lemur).
- Node 24; Playwright já instalado em `e2e/node_modules`.

## Como rodar (resumo)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\AndroidDev\jdk-25.0.5+8"
cd agencia; .\mvnw.cmd test; .\mvnw.cmd package -DskipTests; cd ..
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 eleicao     # Parte A
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 failover    # Parte B
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 historico   # Parte C
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 conflito    # Parte E
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 parar
cd app_mobile; flutter build web --release --no-web-resources-cdn; cd ..
powershell -ExecutionPolicy Bypass -File e2e\rodar-sprint3.ps1           # Partes D, F, adicional, regressão
```

`-Saida evidencias\sprint3\saidas\X.txt` (no `sprint3.ps1`) grava a saída e gera
`evidencias\sprint3\X.png`. A suíte Playwright regrava os prints do app a cada execução.

Armadilhas conhecidas:
- `mvnw package` falha se houver agência rodando (o jar fica travado): `sprint3.ps1 parar` antes.
- A suíte e2e exige as portas 4000-4002 livres (derrube agências antes) e **apaga os logs**
  de `agencia/data` (move para `anteriores-<data>`).
- As agências são iniciadas por `cmd /c` via `Start-Process` sem redirecionamento
  (ShellExecute), para o java não herdar os handles do PowerShell chamador — senão quem chama
  o script trava até as agências caírem.
- Flutter web + Playwright: o app liga a árvore de semântica (`ensureSemantics` em
  `main.dart`); campos são achados por `getByRole('textbox', {name})` e é preciso esperar
  ~400 ms depois do clique antes de digitar (ver `e2e/apoio/app.js`).

## Convenções pedidas pelo usuário (15/11/2026)

- **Sem comentários no código.** Todos foram removidos em 15/11/2026 (Sprints 1-3), com
  validação: 61 testes Java, 10 Dart, 8 Playwright do Sprint 3 e 3 do Sprint 2 passando.
  As justificativas que estavam nos comentários estão nesta página, no README e no RESPOSTAS.
- **Commits sem `Co-Authored-By: Claude` nem `Claude-Session:`.** Em 15/11/2026, com
  autorização do usuário, todo o histórico desde o Sprint 2 teve as mensagens reescritas
  (conteúdo idêntico) e a `main` foi publicada com `git push --force-with-lease`. Quem tiver
  um clone antigo precisa de `git fetch && git reset --hard origin/main`.

## Decisões de projeto (e por quê)

Backend:
1. **Java/Spring** (regra 2.2), código Node do roteiro traduzido; rotas e mensagens iguais.
2. **`java.net.http.HttpClient`** com timeout de conexão e de resposta, atrás da interface
   `ClienteBully` (testes com rede em memória em `BullyServiceTest`).
3. **Concorrência:** `liderAtual` `AtomicInteger` (-1 = nenhuma), eleição em andamento
   `AtomicBoolean` + `compareAndSet`.
4. **O "OK" do Bully volta na hora:** `receberEleicao` responde e conduz a eleição em outra
   thread (senão quem perguntou estoura o timeout e se declara líder).
5. **Anúncio de id menor é recusado** (inicia eleição).
6. **Heartbeat** com `scheduleWithFixedDelay`; reelege também se a líder responde mas não se
   reconhece líder.
7. **Toda mensagem do Bully é logada com vetor**, inclusive envios (`ELEICAO_ENVIADA`,
   `COORDENADOR_ENVIADO`, `AGUARDANDO_COORDENADOR`), por causa da restauração do relógio.
8. **Rotas do Bully públicas**, como no roteiro (discussão na pergunta A3).
9. **Parâmetros configuráveis** (`iceibank.bully.*`; env `BULLY_HEARTBEAT_MS`,
   `BULLY_TIMEOUT_MS`, `BULLY_HABILITADO`, `AGENCIAS_HOST`); desligado nos testes Spring.
10. **Histórico em Java** (`HistoricoLideranca`), como o `MesclarLogs` do Sprint 2; separa
    declarações `TORNOU_LIDER` de trocas efetivas.
11. **Sincronização:** comparação + incremento atômicos (`RelogioVetorial.eventoLocalSeIgual`);
    conflito verificado antes de saldo; snapshot (saldo, vetor) sob a trava da conta
    (`ContaService.comVetor`); `aoReceber` do crédito remoto movido para dentro da trava.
12. **Idempotência por `idOperacao`** (mapa em memória por `dono:id`).
13. **409** = `{erro, relacao, vetorConhecido, vetorAtual, contaAtual}` (handler próprio em
    `TratadorDeErros`). Respostas de conta passaram a ser `ContaComVetor` (campos antigos
    mantidos; o frontend web não mudou).

App:
14. **Hive (`hive_ce`)** com registros JSON (`Box<String>`): roda igual no Android e na web.
15. **Dinheiro em centavos (`int`)**.
16. **Estado com `ChangeNotifier` + `ListenableBuilder`** (sem pacote de estado).
17. **Offline = sem rede (`connectivity_plus`) OU `ErroDeRede` na chamada.** Se a conta já
    tem fila, a nova operação também entra na fila (ordem). Sync ao reconectar e a cada 4 s.
18. **Encadeamento de vetores** entre operações offline da mesma conta (a 2ª usa o vetor
    devolvido pela 1ª).
19. **409 → decisão da pessoa** (diálogo automático + cartão persistente; "aplicar sobre o
    saldo atual" ou "descartar"); operações seguintes da mesma conta também param.
20. **Painel do cluster** (adicional) mede "no ar" e latência com `GET /bully/lider` (leve);
    `GET /status` só complementa com vetor e contas, com timeout de 4 s, porque com o
    RabbitMQ real ele consulta 6 filas no CloudAMQP e passa de 1,5 s (antes isso marcava
    as 3 agências como fora do ar).
21. **Painel da líder** pergunta a uma agência por vez (a última que respondeu primeiro),
    a cada 3 s; mostra quem informou.
22. **Evidências do app** = build web em Chromium (viewport 412×915) + saída real de
    PowerShell ao lado (`e2e/apoio/evidencia.js`).

## Observações das execuções reais (base das RESPOSTAS)

- Subida simultânea (15:48): só a 2 declarou `TORNOU_LIDER`, 4 vezes em ~0,5 s; 7 eleições
  iniciadas e 12 mensagens HTTP entre agências.
- Subida escalonada (16:40): 0 (16:40:23) → 1 (16:40:33) → 2 (16:40:42), 3 trocas efetivas.
- Failover backend (16:04): a 1 detectou às 16:04:46.880, virou líder em 8 ms; a 2 voltou e
  retomou às 16:05:02. A 2 voltou com o vetor restaurado do log (`[4,8,20]`).
- Conflito backend (16:12:43): `[40,49,79]` → depósito → `[41,49,79]` → 409 `ANTES` → reenvio → 200.
- Execução final da suíte do app: 16:39 (todos os prints `app-*`, `funcionalidade-adicional`,
  `regressao-frontend-jwt`).

## Ideias para o Sprint 4 (containers + 2PC/Saga)

- A líder do Bully vira coordenadora do 2PC; aí as rotas `/bully/*` precisam de autenticação
  entre agências (token de serviço ou mTLS) e o "retomar liderança" (pergunta B2) precisa de
  épocas/termos para não haver duas coordenadoras.
- `AGENCIAS_HOST` já permite apontar para nomes de serviço do Docker Compose (mas a porta
  ainda é `4000 + OFFSET + id`; em containers talvez seja melhor uma lista de URLs).
- Conflito por conta em vez de por agência (versão por conta) — pergunta E2.
