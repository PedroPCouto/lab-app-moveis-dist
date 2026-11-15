# ICEIBank - Sprint 3, Parte A: Mensagens de eleição e coordenador

## Conceito

O algoritmo de Bully (Garcia-Molina, 1982) elege sempre o processo de **maior identificador entre os ativos**. Cada agência conhece o ID de todas as outras (0, 1, 2). Quando uma agência decide iniciar uma eleição:

1. Ela envia uma **mensagem de eleição** para todas as agências com ID maior que o seu.
2. Se nenhuma responder, ela mesma vence: vira líder e anuncia isso a todas as outras (**mensagem de coordenador**).
3. Se alguma agência de ID maior responder, essa agência assume a responsabilidade de conduzir a eleição (e eventualmente anunciar o resultado). A agência que perguntou simplesmente espera.
4. Ao receber uma eleição de alguém com ID menor, a agência responde (confirmando que está viva) e inicia sua própria eleição, pois também é candidata.

O resultado é sempre o mesmo: a agência de maior ID entre as ativas se torna líder.

## 5.1 `agencia/src/services/bullyService.js` (parte 1: eleição e coordenador)

```js
import axios from 'axios';
import * as config from '../config.js';

const TIMEOUT_MS = 1000;

function criarServicoBully(idAgencia, relogio, registro) {
  let liderAtual = null;
  let eleicaoEmAndamento = false;

  function agenciasComIdMaior() {
    return config.AGENCIAS.filter((a) => a.id > idAgencia);
  }

  async function iniciarEleicao() {
    if (eleicaoEmAndamento) return;
    eleicaoEmAndamento = true;
    const vetor = relogio.eventoLocal();
    registro.registrar('ELEICAO_INICIADA', vetor, { idAgencia });

    const maiores = agenciasComIdMaior();
    let alguemRespondeu = false;

    for (const agencia of maiores) {
      try {
        const vetorEnvio = relogio.aoEnviar();
        await axios.post(`${agencia.url}/bully/eleicao`, { origemId: idAgencia, vetorEnvio }, { timeout: TIMEOUT_MS });
        alguemRespondeu = true;
      } catch (erro) {
        // agência fora do ar ou não respondeu a tempo - ignora e segue
      }
    }

    eleicaoEmAndamento = false;

    if (!alguemRespondeu) {
      await tornarMeLider();
    }
    // se alguém maior respondeu, é essa agência quem vai conduzir a própria
    // eleição e eventualmente anunciar o resultado via /bully/coordenador
  }

  async function tornarMeLider() {
    liderAtual = idAgencia;
    const vetor = relogio.eventoLocal();
    registro.registrar('TORNOU_LIDER', vetor, { idAgencia });
    for (const agencia of config.AGENCIAS) {
      if (agencia.id === idAgencia) continue;
      try {
        const vetorEnvio = relogio.aoEnviar();
        await axios.post(`${agencia.url}/bully/coordenador`, { liderId: idAgencia, vetorEnvio }, { timeout: TIMEOUT_MS });
      } catch (erro) {
        // agência fora do ar - vai descobrir a líder quando voltar
      }
    }
  }

  function receberEleicao(origemId, vetorRecebido) {
    const vetor = relogio.aoReceber(vetorRecebido);
    registro.registrar('ELEICAO_RECEBIDA', vetor, { de: origemId, minhaId: idAgencia });
    // o 200 da rota já serve como "OK" implícito - e inicio minha própria
    // eleição, pois posso ser a nova líder (tenho ID maior que quem chamou)
    iniciarEleicao();
  }

  function receberCoordenador(liderId, vetorRecebido) {
    liderAtual = liderId;
    const vetor = relogio.aoReceber(vetorRecebido);
    registro.registrar('LIDER_ANUNCIADO', vetor, { liderId, agencia: idAgencia });
  }

  function obterLiderAtual() {
    return liderAtual;
  }

  return { iniciarEleicao, receberEleicao, receberCoordenador, obterLiderAtual };
}

export default criarServicoBully;
```

Este arquivo será completado com o heartbeat na Parte B. Por enquanto, testar apenas a eleição inicial.

## 5.2 `agencia/src/routes.js` (rotas do Bully)

Adicionar estas rotas às já existentes (contas, transferências etc.):

```js
router.post('/bully/eleicao', (req, res) => {
  const { origemId, vetorEnvio } = req.body;
  req.app.locals.bully.receberEleicao(origemId, vetorEnvio);
  res.sendStatus(200);
});

router.post('/bully/coordenador', (req, res) => {
  const { liderId, vetorEnvio } = req.body;
  req.app.locals.bully.receberCoordenador(liderId, vetorEnvio);
  res.sendStatus(200);
});

router.get('/bully/lider', (req, res) => {
  const liderAtual = req.app.locals.bully.obterLiderAtual();
  res.json({ idAgencia: req.app.locals.idAgencia, liderAtual });
});
```

## 5.3 `agencia/src/app.js` (integração)

Adicionar ao `app.js` já existente, mantendo tudo que já está lá (relógio, registro, rotas de contas etc.):

```js
import criarServicoBully from './services/bullyService.js';

// ... depois de criar relogio e registro:
const bully = criarServicoBully(idAgencia, relogio, registro);
app.locals.bully = bully;

// ... dentro do callback de app.listen(porta, () => { ... }):
bully.iniciarEleicao();
```

Ao subir, cada agência já dispara sua própria eleição para descobrir (ou estabelecer) quem é a líder.

## 5.4 Tarefa

1. Subir as 3 agências ao mesmo tempo (em 3 terminais, como nos sprints anteriores).
2. Esperar alguns segundos e consultar `/bully/lider` nas 3:

   ```powershell
   Invoke-RestMethod -Uri "http://localhost:4000/bully/lider" -Method Get
   Invoke-RestMethod -Uri "http://localhost:4001/bully/lider" -Method Get
   Invoke-RestMethod -Uri "http://localhost:4002/bully/lider" -Method Get
   ```

3. Confirmar que as 3 concordam sobre quem é a líder. Deve ser a agência de maior ID que foi subida (normalmente a 2).
4. Observar nos logs do terminal a "tempestade" de eleições na subida. É normal várias agências iniciarem eleições quase ao mesmo tempo; o algoritmo converge de qualquer forma para o ID mais alto.
5. Capturar um print para `evidencias/sprint3/eleicao-inicial.png`.
6. Fazer o commit:

   ```powershell
   git add agencia/src/services/bullyService.js agencia/src/routes.js agencia/src/app.js evidencias/sprint3
   git commit -m "feat(bully): implementa mensagens de eleicao e coordenador"
   ```

## 5.5 Perguntas - Parte A

Responder em `RESPOSTAS.md`:

1. Por que o algoritmo de Bully sempre elege o processo de maior ID, e não o de menor? O resultado seria diferente (mas ainda assim correto) se a regra fosse invertida?
2. No passo 4 da tarefa, você deve ter visto mais de uma agência declarar `TORNOU_LIDER` durante a subida, antes de convergir. Isso é um bug, ou um comportamento esperado do algoritmo? Justifique com base no que você observou nos logs.
3. O endpoint `/bully/eleicao` não tem nenhuma autenticação. Qualquer processo que descubra a URL de uma agência pode disparar uma eleição nela. Isso é um problema de segurança real para este projeto? Por quê, ou por que não?
