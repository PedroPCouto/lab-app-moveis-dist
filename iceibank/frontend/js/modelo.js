window.Modelo = (function () {
  'use strict';

  var NUMERO_AGENCIAS = 3;
  var PORTA_INICIAL = 4000;
  var OFFSET = 0;

  var CHAVE_TOKEN = 'iceibank.token';
  var CHAVE_USUARIO = 'iceibank.usuario';
  var CHAVE_AGENCIA = 'iceibank.agencia';

  var estado = {
    token: null,
    usuario: null,
    nome: null,
    idAgencia: 0
  };

  function ErroApi(mensagem, status) {
    this.name = 'ErroApi';
    this.message = mensagem;
    this.status = status;
  }
  ErroApi.prototype = Object.create(Error.prototype);

  function urlDaAgencia(idAgencia) {
    return 'http://localhost:' + (PORTA_INICIAL + OFFSET + idAgencia);
  }

  function agenciaResponsavel(idConta) {
    return ((idConta % NUMERO_AGENCIAS) + NUMERO_AGENCIAS) % NUMERO_AGENCIAS;
  }

  function listaDeAgencias() {
    var agencias = [];
    for (var id = 0; id < NUMERO_AGENCIAS; id++) {
      agencias.push({ id: id, url: urlDaAgencia(id) });
    }
    return agencias;
  }

  function requisitar(metodo, caminho, corpo, idAgencia) {
    var alvo = typeof idAgencia === 'number' ? idAgencia : estado.idAgencia;
    var opcoes = { method: metodo, headers: {} };

    if (estado.token) {
      opcoes.headers['Authorization'] = 'Bearer ' + estado.token;
    }
    if (corpo !== undefined && corpo !== null) {
      opcoes.headers['Content-Type'] = 'application/json';
      opcoes.body = JSON.stringify(corpo);
    }

    return fetch(urlDaAgencia(alvo) + caminho, opcoes)
      .then(function (resposta) {
        return resposta.text().then(function (texto) {
          var dados = null;
          if (texto) {
            try { dados = JSON.parse(texto); } catch (e) { dados = { erro: texto }; }
          }
          if (!resposta.ok) {
            var mensagem = (dados && dados.erro) ? dados.erro : 'Erro HTTP ' + resposta.status;
            throw new ErroApi(mensagem, resposta.status);
          }
          return dados;
        });
      }, function () {
        throw new ErroApi('Agencia ' + alvo + ' nao respondeu (' + urlDaAgencia(alvo) + '). '
          + 'Ela esta no ar?', 0);
      });
  }

  function entrar(usuario, senha, idAgencia) {
    estado.idAgencia = idAgencia;
    estado.token = null;
    return requisitar('POST', '/auth/login', { usuario: usuario, senha: senha }, idAgencia)
      .then(function (resposta) {
        estado.token = resposta.token;
        estado.usuario = resposta.usuario;
        estado.nome = resposta.nome;
        guardarSessao();
        return resposta;
      });
  }

  function guardarSessao() {
    try {
      localStorage.setItem(CHAVE_TOKEN, estado.token);
      localStorage.setItem(CHAVE_USUARIO, JSON.stringify({ usuario: estado.usuario, nome: estado.nome }));
      localStorage.setItem(CHAVE_AGENCIA, String(estado.idAgencia));
    } catch (e) {
    }
  }

  function restaurarSessao() {
    try {
      var token = localStorage.getItem(CHAVE_TOKEN);
      if (!token) { return false; }
      var usuario = JSON.parse(localStorage.getItem(CHAVE_USUARIO) || '{}');
      estado.token = token;
      estado.usuario = usuario.usuario || null;
      estado.nome = usuario.nome || null;
      estado.idAgencia = parseInt(localStorage.getItem(CHAVE_AGENCIA) || '0', 10);
      return true;
    } catch (e) {
      return false;
    }
  }

  function sair() {
    estado.token = null;
    estado.usuario = null;
    estado.nome = null;
    try {
      localStorage.removeItem(CHAVE_TOKEN);
      localStorage.removeItem(CHAVE_USUARIO);
    } catch (e) {  }
  }

  function definirAgencia(idAgencia) { estado.idAgencia = idAgencia; guardarSessao(); }

  function statusDaAgencia(idAgencia) { return requisitar('GET', '/status', null, idAgencia); }

  function minhasContas() { return requisitar('GET', '/contas', null); }

  function criarConta(id, nomeAluno, saldoInicial) {
    return requisitar('POST', '/contas', { id: id, nomeAluno: nomeAluno, saldoInicial: saldoInicial });
  }

  function depositar(id, valor) {
    return requisitar('POST', '/contas/' + id + '/depositar', { valor: valor });
  }

  function sacar(id, valor) {
    return requisitar('POST', '/contas/' + id + '/sacar', { valor: valor });
  }

  function transferir(idOrigem, idDestino, valor) {
    return requisitar('POST', '/transferencias',
      { idOrigem: idOrigem, idDestino: idDestino, valor: valor });
  }

  function historico(id, limite) {
    return requisitar('GET', '/contas/' + id + '/historico?limite=' + (limite || 20), null);
  }

  function reprocessarMensagensMortas() {
    return requisitar('POST', '/mensagens-mortas/reprocessar', null);
  }

  return {
    NUMERO_AGENCIAS: NUMERO_AGENCIAS,
    estado: estado,
    ErroApi: ErroApi,
    urlDaAgencia: urlDaAgencia,
    agenciaResponsavel: agenciaResponsavel,
    listaDeAgencias: listaDeAgencias,
    entrar: entrar,
    restaurarSessao: restaurarSessao,
    sair: sair,
    definirAgencia: definirAgencia,
    statusDaAgencia: statusDaAgencia,
    minhasContas: minhasContas,
    criarConta: criarConta,
    depositar: depositar,
    sacar: sacar,
    transferir: transferir,
    historico: historico,
    reprocessarMensagensMortas: reprocessarMensagensMortas
  };
})();
