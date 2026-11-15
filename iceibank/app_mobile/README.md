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
| `armazenamento.dart` | armazenamento local com Hive (`hive_ce`): sessão, contas (saldo + `vetorAtual`), fila de pendentes, histórico de líderes |
| `banco.dart` | controlador do fluxo bancário (login com fallback de agência, contas das 3 agências, depósito, saque, transferência, 401 → login) e do **offline-first**: fila, saldo provisório, `sincronizar()` com encadeamento de vetores, conflito → decisão da pessoa |
| `lideranca.dart` | painel da líder: `GET /bully/lider` a cada 3 s, tentando outra agência se a consultada não responder |
| `conectividade.dart` | `connectivity_plus`: sabe se há rede e dispara a sincronização quando ela volta |
| `agencias.dart` | funcionalidade adicional: `GET /status` das 3 agências a cada 5 s (quem está no ar) |
| `telas/pendentes.dart` | faixa offline/sincronizando, fila de pendentes, cartão e diálogo de recusa (409) |
| `telas/painel_cluster.dart` | funcionalidade adicional: agências ativas + histórico de trocas de líder |
| `telas/` | login, tela inicial (painel da líder + contas), diálogos de operação |

## Offline-first em uma frase

Sem rede (ou sem resposta da agência), depósito/saque/transferência viram uma operação
pendente com o `vetorAtual` que o app conhecia; a tela mostra o saldo PROVISÓRIO; quando a
rede volta, a fila vai para `POST /contas/{id}/sincronizar` sozinha; um 409 abre um diálogo
com "saldo que você via × saldo real" e a pessoa escolhe aplicar sobre o saldo atual ou
descartar.

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

## Testes

`flutter test` roda os 10 testes da lógica offline (agência simulada com `MockClient`):
fila, saldo provisório, sincronização ao reconectar, encadeamento de vetores, 409, reenvio
sobre o saldo atual e persistência da fila.

### Ponta a ponta

`..\e2e\rodar-sprint3.ps1` sobe as 3 agências, serve `build/web` e roda
`e2e/sprint3.spec.js`, que gera os prints de `evidencias/sprint3/` e o vídeo
`evidencias/sprint3/videos/sprint3-app.webm`.
