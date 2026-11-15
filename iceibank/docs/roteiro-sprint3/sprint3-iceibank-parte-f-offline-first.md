# ICEIBank - Sprint 3, Parte F: App Flutter - modo offline-first

## Conceito

O app deixa de depender de uma conexão constante com a agência. Dados consultados ficam disponíveis mesmo sem internet, e operações feitas offline são guardadas localmente e sincronizadas automaticamente assim que a conexão volta.

Esta parte também não vem com código de exemplo, pela mesma razão das demais partes do app: a implementação, inclusive a escolha da biblioteca de armazenamento local e a estratégia de resolução de conflito, é 100% do aluno.

## 10.1 Requisitos obrigatórios

- **Armazenamento local:** o app guarda localmente (ex.: `sqflite`, `Hive` ou `Drift`) uma cópia do saldo e do `vetorAtual` mais recente de cada conta consultada, para que a tela de saldo funcione mesmo sem conexão, mostrando o último dado conhecido.
- **Fila de operações pendentes:** se uma operação de escrita (depósito, saque) for feita sem conexão com a agência responsável, ela não falha. É guardada localmente numa fila de pendentes, junto com o `vetorAtual` que o app tinha no momento.
- **UI otimista:** ao registrar uma operação offline, a tela já reflete o efeito esperado imediatamente (ex.: saldo provisório atualizado), com uma indicação visual clara de que está pendente de sincronização.
- **Sincronização automática ao reconectar:** o app detecta quando a conectividade volta (ex.: pacote `connectivity_plus`) e tenta sincronizar a fila pendente automaticamente, sem exigir que a pessoa usuária aperte nada.
- **Tratamento do conflito (HTTP 409):** quando o backend recusa uma sincronização por conflito (seção 9.3), o app deve tratar isso de forma visível. No mínimo, avisar a pessoa usuária de que a operação pendente não pôde ser aplicada automaticamente e mostrar o estado atual e real da conta. Uma estratégia de nova tentativa automática é bem-vinda, mas não obrigatória.

## 10.2 Tarefa

1. Implementar o armazenamento local e a fila de operações pendentes.
2. Colocar o dispositivo/emulador em modo avião (ou desligar o Wi-Fi) e fazer um depósito pelo app. Confirmar que a operação aparece na tela como pendente, sem erro.
3. Capturar um print para `evidencias/sprint3/app-offline.png`.
4. Reativar a conexão e observar o app sincronizar a operação pendente sozinho, sem tocar em nada além de reconectar. Capturar um print (ou dois, antes/depois) para `evidencias/sprint3/app-sincronizacao.png`.
5. Reproduzir também um caso de conflito: fazer uma operação offline pelo app e, antes de reconectá-lo, alterar o saldo da mesma conta por outro caminho (ex.: pelo frontend web ou via `Invoke-RestMethod`). Reconectar o app e confirmar que ele trata o 409 de forma visível, não silenciosa.
6. Fazer o commit:

   ```powershell
   git add app_mobile evidencias/sprint3
   git commit -m "feat(mobile): implementa modo offline-first com fila de sincronizacao"
   ```

## 10.3 Perguntas - Parte F

Responder em `RESPOSTAS.md`:

1. Qual biblioteca de armazenamento local você escolheu, e por quê? Quais alternativas você considerou?
2. Na tarefa 5, o que exatamente aconteceu na tela do app quando a sincronização foi recusada? Descreva o comportamento que você implementou e explique por que escolheu essa abordagem em vez de outra.
3. UI otimista significa mostrar o resultado esperado antes de ter certeza de que ele vai acontecer. Que risco isso traz para um aplicativo bancário, especificamente? Como seu design tenta reduzir esse risco?
