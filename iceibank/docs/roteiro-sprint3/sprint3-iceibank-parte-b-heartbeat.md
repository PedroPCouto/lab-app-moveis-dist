# ICEIBank - Sprint 3, Parte B: Heartbeat e failover automático

## Conceito

Eleger uma líder uma vez não é suficiente: o sistema precisa perceber quando ela cai e reagir sozinho. Cada agência passa a verificar periodicamente (**heartbeat**) se a líder que ela conhece ainda está respondendo. Se não estiver, ela esquece a líder antiga e inicia uma nova eleição.

## 6.1 Completando `agencia/src/services/bullyService.js`

Adicionar estas funções dentro de `criarServicoBully` (antes do `return` final), e incluir `verificarLider` e `iniciarHeartbeat` no objeto retornado:

```javascript
  async function verificarLider() {
    if (liderAtual === null) {
      await iniciarEleicao();
      return;
    }
    if (liderAtual === idAgencia) return; // eu sou a lider, nada a verificar

    const liderInfo = config.AGENCIAS.find((a) => a.id === liderAtual);
    try {
      await axios.get(`${liderInfo.url}/bully/lider`, { timeout: TIMEOUT_MS });
    } catch (erro) {
      const vetor = relogio.eventoLocal();
      registro.registrar('LIDER_NAO_RESPONDEU', vetor, { liderAtual, agencia: idAgencia });
      liderAtual = null;
      await iniciarEleicao();
    }
  }

  function iniciarHeartbeat(intervaloMs = 3000) {
    setInterval(verificarLider, intervaloMs);
  }
```

Atualizar o `return` do final da função:

```javascript
  return { iniciarEleicao, receberEleicao, receberCoordenador, obterLiderAtual, iniciarHeartbeat };
```

## 6.2 Completando `agencia/src/app.js`

Adicionar, logo após `bully.iniciarEleicao();`:

```javascript
bully.iniciarHeartbeat();
```

## 6.3 Tarefa

1. Subir as 3 agências e confirmar a eleição inicial (como na Parte A).
2. Derrubar a agência líder (fechar o terminal dela).
3. Esperar alguns segundos (o heartbeat roda a cada 3 segundos, por padrão) e consultar `/bully/lider` nas 2 agências restantes.
4. Confirmar que ambas concordam sobre uma nova líder: a de maior ID entre as que sobraram.
5. Capturar um print da sequência (derrubar a líder + consultar o novo resultado) para `evidencias/sprint3/failover.png`.
6. Subir a agência derrubada de novo. Ela vai iniciar sua própria eleição ao subir. Se o ID dela for maior que o da líder atual, uma nova troca de liderança deve acontecer.
7. Fazer o commit:

   ```powershell
   git add agencia/src/services/bullyService.js agencia/src/app.js evidencias/sprint3
   git commit -m "feat(bully): adiciona heartbeat e failover automatico"
   ```

## 6.4 Perguntas - Parte B

Responder em `RESPOSTAS.md`:

1. O que aconteceria se o intervalo do heartbeat (`intervaloMs`) fosse muito curto (ex.: 100ms)? E se fosse muito longo (ex.: 60 segundos)? Pense no trade-off entre detectar falhas rápido e sobrecarregar a rede com verificações.
2. No passo 6 da tarefa, a agência que você derrubou e subiu de novo pode, dependendo do ID dela, "tomar" a liderança de volta mesmo que a eleição atual estivesse funcionando bem. Isso é desejável? Que problema prático isso pode causar em um sistema real (pense em uma troca de liderança acontecendo bem no meio de uma operação importante)?
3. O `TIMEOUT_MS` de 1 segundo é usado tanto para detectar uma agência fora do ar quanto para decidir se alguém respondeu a uma eleição. O que aconteceria se a rede estivesse genuinamente lenta (não a agência fora do ar, só uma resposta demorada) e isso ultrapassasse 1 segundo?
