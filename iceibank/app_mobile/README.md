# ICEIBank — app mobile (Flutter)

Cliente mobile do ICEIBank (Sprint 3). Consome a mesma API REST das agências que o
frontend web usa, e mostra qual agência é a **líder** eleita pelo algoritmo de Bully.

- Flutter 3.50 / Dart 3.14. Material vem do pacote `material_ui` (no Flutter 3.50 o
  Material saiu do SDK).
- Plataformas geradas: **Android** e **web**. As evidências foram capturadas com o build
  web num Chromium com viewport de celular (a máquina de desenvolvimento não tem emulador).

## Estrutura (`lib/src/`)

| arquivo | papel |
|---|---|
| `config.dart` | onde estão as agências (`host`, porta `4000 + OFFSET + id`, conta `N` → agência `N % 3`) e os intervalos de polling |
| `api.dart` | cliente HTTP: JWT em toda requisição, `ErroApi` (a agência respondeu com erro) × `ErroDeRede` (não respondeu) |
| `modelos.dart` | `Conta` (saldo em centavos + `vetorAtual`), `Sessao`, `TrocaDeLider`, formatação de R$ |
| `armazenamento.dart` | armazenamento local com Hive (`hive_ce`): sessão, contas, histórico de líderes |
| `banco.dart` | controlador do fluxo bancário (login com fallback de agência, contas das 3 agências, depósito, saque, transferência, 401 → login) |
| `lideranca.dart` | painel da líder: `GET /bully/lider` a cada 3 s, tentando outra agência se a consultada não responder |
| `telas/` | login, tela inicial (painel da líder + contas), diálogos de operação |

## Rodar

```powershell
flutter pub get
flutter run -d chrome                                   # navegador, agências em localhost
flutter run -d emulator-5554                            # emulador Android: usa 10.0.2.2 sozinho
flutter run --dart-define=ICEIBANK_HOST=192.168.0.10    # celular físico: IP do PC
flutter build web --release --no-web-resources-cdn      # build usado pelos testes e2e
```

O host também pode ser trocado na tela de login ("Servidor das agências").
Usuários de laboratório: `ana/ana123`, `bruno/bruno123`, `carla/carla123`.

## Testes ponta a ponta

`..\e2e\rodar-sprint3.ps1` sobe as 3 agências, serve `build/web` e roda
`e2e/sprint3.spec.js`, que gera os prints de `evidencias/sprint3/` e o vídeo
`evidencias/sprint3/videos/sprint3-app.webm`.
