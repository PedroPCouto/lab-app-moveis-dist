(function () {
  'use strict';

  var elemento = Visao.elemento;

  function tratarErro(erro) {
    if (erro instanceof Modelo.ErroApi && erro.status === 401) {
      Modelo.sair();
      Visao.mostrarLogin(Modelo.listaDeAgencias(), Modelo.estado.idAgencia);
      Visao.mostrarMensagem('alerta', 'Sua sessao expirou ou o token nao e mais valido. '
        + 'Entre novamente. (detalhe da API: ' + erro.message + ')');
      return;
    }
    Visao.mostrarMensagem('erro', erro.message || 'Erro inesperado.');
  }

  function atualizarContas() {
    return Modelo.minhasContas().then(Visao.renderizarContas);
  }

  function atualizarStatusDasAgencias() {
    var consultas = Modelo.listaDeAgencias().map(function (agencia) {
      return Modelo.statusDaAgencia(agencia.id)
        .then(function (status) {
          return {
            id: agencia.id,
            noAr: true,
            relogioVetorial: status.relogioVetorial,
            quantidadeContas: status.quantidadeContas,
            filas: status.filas
          };
        })
        .catch(function () { return { id: agencia.id, noAr: false }; });
    });
    return Promise.all(consultas).then(function (situacoes) {
      Visao.renderizarStatusDasAgencias(situacoes, Modelo.estado.idAgencia);
      Visao.renderizarFilas(Modelo.estado.idAgencia, situacoes[Modelo.estado.idAgencia].filas);
    });
  }

  function atualizarTudo() {
    return atualizarContas().then(atualizarStatusDasAgencias).catch(tratarErro);
  }

  function abrirApp() {
    Visao.mostrarApp(Modelo.estado, Modelo.listaDeAgencias());
    atualizarTudo();
  }

  elemento('formularioLogin').addEventListener('submit', function (evento) {
    evento.preventDefault();
    Visao.limparMensagem();

    var idAgencia = parseInt(elemento('agenciaLogin').value, 10);
    Modelo.entrar(elemento('usuario').value, elemento('senha').value, idAgencia)
      .then(function (sessao) {
        Visao.mostrarMensagem('ok', 'Bem-vindo(a), ' + sessao.nome
          + '. Token valido por ' + Math.round(sessao.expiraEmSegundos / 60) + ' minutos.');
        abrirApp();
      })
      .catch(function (erro) {
        Visao.mostrarMensagem('erro', erro.message);
      });
  });

  elemento('botaoSair').addEventListener('click', function () {
    Modelo.sair();
    Visao.mostrarLogin(Modelo.listaDeAgencias(), Modelo.estado.idAgencia);
    Visao.mostrarMensagem('ok', 'Sessao encerrada.');
  });

  elemento('agenciaAtual').addEventListener('change', function (evento) {
    var idAgencia = parseInt(evento.target.value, 10);
    Modelo.definirAgencia(idAgencia);
    Visao.mostrarMensagem('alerta', 'Porta de entrada trocada para a agencia ' + idAgencia
      + '. O token continua valido: as 3 agencias assinam com a mesma chave.');
    abrirApp();
  });

  elemento('botaoAtualizar').addEventListener('click', atualizarTudo);

  elemento('novaContaId').addEventListener('input', function (evento) {
    var id = parseInt(evento.target.value, 10);
    if (isNaN(id)) { return Visao.definirDica('dicaParticao', ''); }
    var responsavel = Modelo.agenciaResponsavel(id);
    Visao.definirDica('dicaParticao', 'Conta ' + id + ' pertence a agencia ' + responsavel
      + (responsavel === Modelo.estado.idAgencia
        ? ' - e esta agencia, pode criar aqui.'
        : ' - troque a porta de entrada para a agencia ' + responsavel + ' antes de criar.'));
  });

  elemento('formularioConta').addEventListener('submit', function (evento) {
    evento.preventDefault();
    Visao.limparMensagem();

    var id = parseInt(elemento('novaContaId').value, 10);
    Modelo.criarConta(id, elemento('novaContaNome').value,
        Number(elemento('novaContaSaldo').value || 0))
      .then(function (conta) {
        Visao.mostrarMensagem('ok', 'Conta ' + conta.id + ' criada com saldo '
          + Visao.moeda(conta.saldo) + '.');
        Visao.limparFormulario('formularioConta');
        Visao.definirDica('dicaParticao', '');
        atualizarTudo();
      })
      .catch(tratarErro);
  });

  var operacaoEscolhida = 'depositar';
  elemento('formularioMovimento').addEventListener('click', function (evento) {
    if (evento.target.dataset && evento.target.dataset.operacao) {
      operacaoEscolhida = evento.target.dataset.operacao;
    }
  });

  elemento('formularioMovimento').addEventListener('submit', function (evento) {
    evento.preventDefault();
    Visao.limparMensagem();

    var id = parseInt(elemento('movimentoConta').value, 10);
    var valor = Number(elemento('movimentoValor').value);
    var operacao = operacaoEscolhida === 'sacar' ? Modelo.sacar : Modelo.depositar;

    operacao(id, valor)
      .then(function (conta) {
        Visao.mostrarMensagem('ok', (operacaoEscolhida === 'sacar' ? 'Saque' : 'Deposito') + ' de '
          + Visao.moeda(valor) + ' na conta ' + conta.id + '. Novo saldo: '
          + Visao.moeda(conta.saldo) + '.');
        atualizarTudo();
      })
      .catch(tratarErro);
  });

  function atualizarDicaDeTransferencia() {
    var origem = parseInt(elemento('transferenciaOrigem').value, 10);
    var destino = parseInt(elemento('transferenciaDestino').value, 10);
    if (isNaN(origem) || isNaN(destino)) { return Visao.definirDica('dicaTransferencia', ''); }

    var agenciaOrigem = Modelo.agenciaResponsavel(origem);
    var agenciaDestino = Modelo.agenciaResponsavel(destino);
    Visao.definirDica('dicaTransferencia', agenciaOrigem === agenciaDestino
      ? 'Ambas na agencia ' + agenciaOrigem + ': sera uma transferencia LOCAL.'
      : 'Agencia ' + agenciaOrigem + ' -> agencia ' + agenciaDestino
        + ': sera uma transferencia ENTRE AGENCIAS.');
  }

  elemento('transferenciaOrigem').addEventListener('input', atualizarDicaDeTransferencia);
  elemento('transferenciaDestino').addEventListener('input', atualizarDicaDeTransferencia);

  elemento('formularioTransferencia').addEventListener('submit', function (evento) {
    evento.preventDefault();
    Visao.limparMensagem();

    var origem = parseInt(elemento('transferenciaOrigem').value, 10);
    var destino = parseInt(elemento('transferenciaDestino').value, 10);
    var valor = Number(elemento('transferenciaValor').value);

    Modelo.transferir(origem, destino, valor)
      .then(function (resultado) {
        var rotulo = resultado.escopo === 'LOCAL'
          ? 'TRANSFERENCIA LOCAL (mesma agencia ' + resultado.agenciaDestino + ')'
          : 'TRANSFERENCIA ENTRE AGENCIAS (agencia ' + Modelo.estado.idAgencia
            + ' -> agencia ' + resultado.agenciaDestino + ') publicada no RabbitMQ:'
            + ' o credito e aplicado de forma assincrona pela agencia de destino';
        Visao.mostrarMensagem('ok', rotulo + '\n'
          + Visao.moeda(resultado.valor) + ' da conta ' + resultado.idOrigem
          + ' para a conta ' + resultado.idDestino + '.\n'
          + 'Saldo da origem: ' + Visao.moeda(resultado.saldoOrigem)
          + '  |  Vetor do evento: ' + JSON.stringify(resultado.timestampVetorial));
        atualizarTudo();
      })
      .catch(tratarErro);
  });

  elemento('botaoReprocessar').addEventListener('click', function () {
    Visao.limparMensagem();
    Modelo.reprocessarMensagensMortas()
      .then(function (resultado) {
        Visao.mostrarMensagem(resultado.reprocessadas > 0 ? 'ok' : 'alerta',
          resultado.reprocessadas > 0
            ? resultado.reprocessadas + ' mensagem(ns) morta(s) devolvida(s) para a fila da agencia '
              + Modelo.estado.idAgencia + '. O credito e aplicado assim que for consumido.'
            : 'Nenhuma mensagem morta na agencia ' + Modelo.estado.idAgencia + '.');
        // o consumo e assincrono: espera um instante antes de reler saldos e filas
        setTimeout(atualizarTudo, 1000);
      })
      .catch(tratarErro);
  });

  function carregarHistorico(idConta) {
    elemento('historicoConta').value = idConta;
    Modelo.historico(idConta, 30)
      .then(function (resposta) { Visao.renderizarHistorico(idConta, resposta.eventos); })
      .catch(tratarErro);
  }

  elemento('formularioHistorico').addEventListener('submit', function (evento) {
    evento.preventDefault();
    Visao.limparMensagem();
    carregarHistorico(parseInt(elemento('historicoConta').value, 10));
  });

  elemento('corpoContas').addEventListener('click', function (evento) {
    if (evento.target.dataset && evento.target.dataset.conta) {
      carregarHistorico(parseInt(evento.target.dataset.conta, 10));
    }
  });

  if (Modelo.restaurarSessao()) {
    abrirApp();
  } else {
    Visao.mostrarLogin(Modelo.listaDeAgencias(), 0);
  }
})();
