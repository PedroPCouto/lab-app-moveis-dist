# Sprint 3 — estado atual, decisões e como continuar

> Documento de passagem de bastão. Qualquer pessoa (ou modelo de IA) que pegar o projeto
> no meio do Sprint 3 deve começar por aqui. Atualize-o a cada etapa concluída.

Roteiro original do Sprint 3: [`docs/roteiro-sprint3/`](roteiro-sprint3/) (cópia fiel dos
arquivos entregues pelo professor). Checklist e critérios de avaliação estão em
`sprint3-iceibank-adaptacao-checklist-avaliacao.md`.

## Progresso (checklist da seção 12)

| Item | Situação | Onde |
|---|---|---|
| Parte A — eleição/coordenador (Bully) | feito | `agencia/.../service/BullyService.java`, `BullyController.java`, evidência `evidencias/sprint3/eleicao-inicial.png` |
| 3 agências convergem para a mesma líder | feito (verificado em execução real) | idem |
| Parte B — heartbeat + reeleição automática | feito | `BullyService.verificarLider/iniciarHeartbeat`, `failover.png` |
| Parte C — histórico de lideranças | feito (em Java: `tools/HistoricoLideranca.java`) | `historico-lideranca.png` |
| Parte E — `/contas/{id}/sincronizar` + 409 | em andamento | — |
| Parte D — app Flutter (fluxo bancário + painel da líder) | pendente | `app_mobile/` |
| Parte F — offline-first no app | pendente | `app_mobile/` |
| Funcionalidade adicional | pendente (escolha: indicador de agências ativas no app) | — |
| RESPOSTAS.md (5.5, 6.4, 7.3, 8.3, 9.6, 10.3) | pendente | `RESPOSTAS.md` |
| README atualizado | pendente | `README.md` |

## Ambiente desta máquina (Windows 11, disco C: quase cheio — ~5 GB livres)

- **JDK 25** portátil em `%LOCALAPPDATA%\AndroidDev\jdk-25.0.5+8` (o `pom.xml` exige Java 25).
  Use `JAVA_HOME` apontando para lá. Há também um JDK 21 em `AndroidDev\jdk-21` (não serve).
- **Flutter 3.50.0 / Dart 3.14** em `%LOCALAPPDATA%\AndroidDev\flutter` (clone raso da tag
  3.50.0, só artefatos **web** baixados: `flutter precache --web`). Android/iOS/desktop
  desabilitados no `flutter config` para economizar disco.
- **Sem emulador Android** (não há imagem de sistema nem espaço em disco para uma). O
  Android SDK em `%LOCALAPPDATA%\Android\Sdk` tem só platform-tools/build-tools.
  Decisão: o app é desenvolvido e evidenciado como **Flutter web** rodando no Chromium do
  Playwright, com viewport de celular; o mesmo código roda em Android (ver README do app).
- **Sem RabbitMQ**: a `RABBITMQ_URL` (CloudAMQP) não está definida nesta máquina. O
  `scripts/sprint3.ps1` sobe as agências com `amqp://localhost` (sem broker): Bully, contas
  e sincronização funcionam; só a transferência **entre** agências responde 503 (estorno).
  Para a regressão do Sprint 2 (mensageria) é preciso a URL do usuário:
  `[Environment]::SetEnvironmentVariable('RABBITMQ_URL','amqps://...','User')`.
  **Nunca** grave essa URL em arquivo.
- Node 24 disponível (Playwright já instalado em `e2e/`).

## Como rodar (resumo)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\AndroidDev\jdk-25.0.5+8"
cd agencia; .\mvnw.cmd test; .\mvnw.cmd package -DskipTests; cd ..
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 eleicao     # sobe as 3 e consulta /bully/lider
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 failover    # derruba a líder, espera, religa
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 historico   # Parte C
powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 parar       # derruba todas
```

`-Saida evidencias\sprint3\saidas\X.txt` grava a saída e gera `evidencias\sprint3\X.png`.

Armadilha conhecida: o `mvnw package` falha se houver agência rodando (o jar fica travado).
Derrube-as antes (`sprint3.ps1 parar`).

## Decisões de projeto do Sprint 3 (e por quê)

1. **Backend continua em Java/Spring** (regra 2.2 do escopo). O código Node do roteiro foi
   traduzido; a lógica de decisão do Bully é a mesma, rotas e formato de mensagem idênticos
   (`POST /bully/eleicao {origemId, vetorEnvio}`, `POST /bully/coordenador {liderId,
   vetorEnvio}`, `GET /bully/lider`).
2. **HTTP entre agências com `java.net.http.HttpClient`** + timeout (connect e request),
   atrás da interface `ClienteBully` para testar a lógica sem rede (`BullyServiceTest`).
3. **Concorrência:** `liderAtual` é `AtomicInteger` (-1 = sem líder); "eleição em
   andamento" é `AtomicBoolean` com `compareAndSet`.
4. **O "OK" do Bully volta imediatamente:** `receberEleicao` responde 200 e conduz a
   própria eleição numa thread separada. Senão quem perguntou estoura o timeout de 1 s e
   se declara líder por engano.
5. **Anúncio de id menor é recusado** (`receberCoordenador` com `liderId < meuId` inicia
   eleição). Sem isso, a tempestade da subida podia convergir para a agência errada.
6. **Heartbeat** com `scheduleWithFixedDelay` (não sobrepõe verificações) e checagem
   extra: se a líder responde mas diz que não é mais líder, também reelege.
7. **Toda mensagem do Bully passa pelo relógio vetorial e é logada** (inclusive os envios:
   `ELEICAO_ENVIADA`, `COORDENADOR_ENVIADO`, além de `AGUARDANDO_COORDENADOR`). Motivo: o
   relógio é restaurado do último evento logado ao reiniciar; envio não logado faria a
   agência reutilizar vetores já vistos pelas outras.
8. **Rotas do Bully públicas** (sem JWT), como no roteiro — discutido na pergunta 3 da Parte A.
9. **Parâmetros configuráveis** (`iceibank.bully.*` / env `BULLY_HEARTBEAT_MS`,
   `BULLY_TIMEOUT_MS`, `BULLY_HABILITADO`, `AGENCIAS_HOST`). Nos testes Spring o Bully fica
   desligado (`iceibank.bully.habilitado=false`).
10. **Script de histórico em Java** (`HistoricoLideranca`, `exec:java@historico-lideranca`),
    seguindo o precedente do `MesclarLogs` do Sprint 2.
11. **Scripts de demonstração em PowerShell** (`scripts/sprint3.ps1`), porque os `.sh` do
    Sprint 2 dependem de jq/curl/Python que esta máquina não tem. As agências são iniciadas
    via `cmd /c` com `Start-Process` sem redirecionamento (ShellExecute), para o java não
    herdar os handles do PowerShell chamador (senão o chamador trava até as agências caírem).

## Observações das execuções reais (para as RESPOSTAS)

- Subida simultânea (15/11/2026 15:48 e 16:04): só a agência 2 declarou `TORNOU_LIDER`,
  **4 vezes** em ~0,5 s (uma reeleição por eleição recebida de 0 e de 1). 0 e 1 receberam
  4 `LIDER_ANUNCIADO` cada. Convergência para 2 em < 1 s.
- Failover (16:04:48): `Stop-Process` na 2; a agência 1 detectou às 16:04:46.88 (log em
  UTC 19:04:46.880, "sem resposta em 1000 ms"), virou líder em 8 ms; a 0 aceitou. Religada,
  a 2 retomou a liderança (`TORNOU_LIDER` 19:05:02). Histórico: 6 declarações, 3 trocas
  efetivas.
- O vetor da agência 2 sobreviveu ao reinício (voltou em `[4,8,20]`, continuando o log).
