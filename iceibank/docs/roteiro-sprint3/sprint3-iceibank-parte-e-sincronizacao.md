# ICEIBank - Sprint 3, Parte E: Sincronização offline-first no backend

## Conceito

Para o app poder operar offline com segurança, a agência precisa saber se uma operação feita "no passado" (enquanto o app estava desconectado) ainda é segura de aplicar, ou se o estado da conta já mudou desde então. O **relógio vetorial do Sprint 2** resolve exatamente esse problema: o app guarda o vetor que a agência tinha na última vez em que leu a conta e devolve esse vetor junto quando sincroniza. A agência compara esse "vetor conhecido" com o seu vetor atual:

- Se forem **iguais**, nada mudou desde a última leitura do app: a operação é aplicada normalmente.
- Se o vetor atual da agência **avançou**, algo aconteceu enquanto o app estava offline: a agência recusa aplicar cegamente e devolve o estado atual, para o app decidir o que fazer.

## 9.1 Lendo o vetor atual: `agencia/src/services/vectorClock.js` (atualizado)

Adicionar este método à classe `RelogioVetorial` já existente. Ele não altera as regras de `eventoLocal`/`aoEnviar`/`aoReceber` do Sprint 2; só adiciona uma forma de ler o vetor sem incrementá-lo:

```javascript
  obterVetorAtual() {
    return [...this.vetor];
  }
```

## 9.2 `agencia/src/services/compararVetores.js`

A mesma lógica de comparação usada no `mesclar-logs.js` do Sprint 2, agora reaproveitada em tempo real pelo backend:

```javascript
function compararVetores(v1, v2) {
  let v1MenorOuIgual = true;
  let v2MenorOuIgual = true;
  for (let i = 0; i < v1.length; i++) {
    if (v1[i] > v2[i]) v1MenorOuIgual = false;
    if (v2[i] > v1[i]) v2MenorOuIgual = false;
  }
  if (v1MenorOuIgual && v2MenorOuIgual) return 'IGUAIS';
  if (v1MenorOuIgual) return 'ANTES';
  if (v2MenorOuIgual) return 'DEPOIS';
  return 'CONCORRENTES';
}

export default compararVetores;
```

## 9.3 `agencia/src/controllers/contasController.js` (atualizado)

A consulta de saldo passa a devolver o vetor atual (para o app guardar), e um novo endpoint de sincronização usa esse vetor para detectar conflito:

```javascript
import * as config from '../config.js';
import compararVetores from '../services/compararVetores.js';

// ... criarConta, depositar e sacar continuam iguais ao Sprint 1 ...

function consultarSaldo(req, res) {
  const { contas, relogio } = req.app.locals;
  const id = parseInt(req.params.id, 10);
  const conta = contas.get(id);
  if (!conta) return res.status(404).json({ erro: 'Conta não encontrada nesta agência.' });
  res.json({ ...conta, vetorAtual: relogio.obterVetorAtual() });
}

function sincronizarOperacaoPendente(req, res) {
  const { contas, relogio, registro } = req.app.locals;
  const id = parseInt(req.params.id, 10);
  const { tipo, valor, vetorConhecido } = req.body;

  const conta = contas.get(id);
  if (!conta) return res.status(404).json({ erro: 'Conta não encontrada nesta agência.' });

  const vetorAtual = relogio.obterVetorAtual();
  const relacao = compararVetores(vetorConhecido, vetorAtual);

  if (relacao !== 'IGUAIS') {
    // outras operações aconteceram na agência desde a última sincronização do
    // app - não aplica cegamente, devolve o estado atual para o app decidir
    return res.status(409).json({
      erro: 'Conflito detectado: o estado da conta mudou desde a última sincronização do app.',
      vetorConhecido,
      vetorAtual,
      contaAtual: conta,
    });
  }

  let vetor;
  if (tipo === 'DEPOSITO') {
    vetor = relogio.eventoLocal();
    conta.saldo += valor;
  } else if (tipo === 'SAQUE') {
    if (conta.saldo < valor) return res.status(400).json({ erro: 'Saldo insuficiente.' });
    vetor = relogio.eventoLocal();
    conta.saldo -= valor;
  } else {
    return res.status(400).json({ erro: 'Tipo de operação pendente não suportado.' });
  }

  registro.registrar('SINCRONIZACAO_OFFLINE', vetor, { id, tipo, valor });
  res.json(conta);
}

export { criarConta, consultarSaldo, depositar, sacar, sincronizarOperacaoPendente };
```

O exemplo cobre depósito e saque. Estender a mesma lógica para **transferências** fica por conta do aluno: a comparação de vetores é idêntica, só muda o que acontece quando a operação é aplicada.

## 9.4 `agencia/src/routes.js` (nova rota)

```javascript
router.post('/contas/:id/sincronizar', contasController.sincronizarOperacaoPendente);
```

## 9.5 Tarefa

1. Criar uma conta e consultar seu saldo. Confirmar que a resposta agora inclui `vetorAtual`.
2. Guardar esse vetor à mão (ou com um script simples) e fazer um depósito diretamente, simulando uma mudança que aconteceu "enquanto o app estava offline".
3. Tentar sincronizar uma operação usando o vetor antigo (de antes do depósito) e confirmar que recebe **409**: o conflito foi detectado.
4. Capturar um print dessa resposta para `evidencias/sprint3/conflito-sincronizacao.png`.
5. Sincronizar de novo, usando o `vetorAtual` devolvido na resposta de conflito, e confirmar que a operação é aplicada com sucesso.
6. Fazer o commit:

   ```powershell
   git add agencia/src evidencias/sprint3
   git commit -m "feat(offline-first): adiciona endpoint de sincronizacao com deteccao de conflito via vetor"
   ```

## 9.6 Perguntas - Parte E

Responder em `RESPOSTAS.md`:

1. Por que a agência compara o vetor inteiro, e não só verifica se o saldo da conta mudou? O que a comparação de vetores detecta que uma simples comparação de saldo não detectaria?
2. Neste exemplo, qualquer mudança no vetor da agência (mesmo em uma operação de outra conta, que nada tem a ver com a sua) é tratada como conflito. Isso é uma simplificação. Explique por que ela existe (lembre-se de que o vetor é por agência, não por conta) e o que seria necessário para tornar a detecção mais precisa, por conta.
3. Quando a sincronização é recusada (409), a agência devolve o estado atual da conta. O que o app deveria fazer com essa informação: descartar a operação pendente, tentar de novo automaticamente, ou perguntar à pessoa usuária? Justifique sua escolha de design na Parte F.
