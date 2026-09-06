window.Visao = (function () {
  'use strict';

  function elemento(id) { return document.getElementById(id); }

  var temporizadorMensagem = null;

  function mostrarMensagem(tipo, texto) {
    var caixa = elemento('mensagem');
    caixa.textContent = texto;
    caixa.className = 'mensagem--' + tipo;
    caixa.hidden = false;
    clearTimeout(temporizadorMensagem);
    if (tipo === 'ok') {
      temporizadorMensagem = setTimeout(limparMensagem, 8000);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function limparMensagem() { elemento('mensagem').hidden = true; }

  function moeda(valor) {
    return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function preencherSeletorDeAgencias(seletor, agencias, selecionada) {
    seletor.innerHTML = '';
    agencias.forEach(function (agencia) {
      var opcao = document.createElement('option');
      opcao.value = String(agencia.id);
      opcao.textContent = 'Agencia ' + agencia.id + '  -  ' + agencia.url;
      if (agencia.id === selecionada) { opcao.selected = true; }
      seletor.appendChild(opcao);
    });
  }

  function mostrarLogin(agencias, agenciaSelecionada) {
    elemento('telaLogin').hidden = false;
    elemento('telaApp').hidden = true;
    elemento('identificacao').hidden = true;
    preencherSeletorDeAgencias(elemento('agenciaLogin'), agencias, agenciaSelecionada);
  }

  function mostrarApp(sessao, agencias) {
    elemento('telaLogin').hidden = true;
    elemento('telaApp').hidden = false;
    elemento('identificacao').hidden = false;
    elemento('rotuloUsuario').textContent =
      (sessao.nome || sessao.usuario) + ' - agencia ' + sessao.idAgencia;
    preencherSeletorDeAgencias(elemento('agenciaAtual'), agencias, sessao.idAgencia);
  }

  function renderizarContas(contas) {
    var corpo = elemento('corpoContas');
    corpo.innerHTML = '';
    elemento('contasVazias').hidden = contas.length > 0;

    contas.forEach(function (conta) {
      var linha = document.createElement('tr');

      var celulaId = document.createElement('td');
      celulaId.textContent = conta.id;

      var celulaNome = document.createElement('td');
      celulaNome.textContent = conta.nomeAluno;

      var celulaSaldo = document.createElement('td');
      celulaSaldo.className = 'saldo';
      celulaSaldo.style.textAlign = 'right';
      celulaSaldo.textContent = moeda(conta.saldo);

      var celulaAcao = document.createElement('td');
      var botao = document.createElement('button');
      botao.className = 'secundario';
      botao.style.cssText = 'width:auto;margin:0;padding:3px 10px;font-size:.8rem';
      botao.textContent = 'historico';
      botao.dataset.conta = String(conta.id);
      celulaAcao.appendChild(botao);

      linha.append(celulaId, celulaNome, celulaSaldo, celulaAcao);
      corpo.appendChild(linha);
    });
  }

  function renderizarStatusDasAgencias(situacoes, agenciaAtual) {
    var caixa = elemento('pilulasAgencias');
    caixa.innerHTML = '';
    situacoes.forEach(function (situacao) {
      var pilula = document.createElement('span');
      pilula.className = 'pilula';
      if (!situacao.noAr) { pilula.className += ' pilula--fora'; }
      else if (situacao.id === agenciaAtual) { pilula.className += ' pilula--atual'; }

      pilula.textContent = situacao.noAr
        ? 'Agencia ' + situacao.id + ' - Lamport ' + situacao.relogioLamport
          + ' - ' + situacao.quantidadeContas + ' conta(s)'
        : 'Agencia ' + situacao.id + ' - fora do ar';
      caixa.appendChild(pilula);
    });
  }

  function renderizarHistorico(idConta, eventos) {
    var corpo = elemento('corpoHistorico');
    var vazio = elemento('historicoVazio');
    corpo.innerHTML = '';

    if (!eventos.length) {
      vazio.hidden = false;
      vazio.textContent = 'Nenhum evento registrado para a conta ' + idConta + '.';
      return;
    }
    vazio.hidden = true;

    eventos.forEach(function (evento) {
      var linha = document.createElement('tr');
      [
        evento.timestampLamport,
        evento.agencia,
        evento.tipo,
        JSON.stringify(evento.detalhes),
        evento.horaParede
      ].forEach(function (valor, indice) {
        var celula = document.createElement('td');
        if (indice === 3 || indice === 4) {
          var codigo = document.createElement('code');
          codigo.textContent = valor;
          celula.appendChild(codigo);
        } else {
          celula.textContent = valor;
        }
        linha.appendChild(celula);
      });
      corpo.appendChild(linha);
    });
  }

  function definirDica(id, texto) { elemento(id).textContent = texto; }

  function limparFormulario(id) { elemento(id).reset(); }

  return {
    elemento: elemento,
    moeda: moeda,
    mostrarMensagem: mostrarMensagem,
    limparMensagem: limparMensagem,
    mostrarLogin: mostrarLogin,
    mostrarApp: mostrarApp,
    renderizarContas: renderizarContas,
    renderizarStatusDasAgencias: renderizarStatusDasAgencias,
    renderizarHistorico: renderizarHistorico,
    definirDica: definirDica,
    limparFormulario: limparFormulario
  };
})();
