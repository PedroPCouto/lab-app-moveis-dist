import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api.dart';
import 'armazenamento.dart';
import 'config.dart';
import 'modelos.dart';

/// Fluxo bancario do app (Parte D): login, contas, deposito, saque e transferencia,
/// consumindo a mesma API REST do frontend web. Mesma separacao do frontend do Sprint 1:
/// este controlador e o "modelo + controle"; as telas so desenham o estado e chamam metodos.
class Banco extends ChangeNotifier {
  final ApiCliente api;
  final Armazenamento armazenamento;

  Sessao? sessao;
  List<Conta> contas = [];

  /// Agencias que nao responderam na ultima recarga: as contas delas aparecem com o
  /// ultimo saldo conhecido (cache local).
  Set<int> agenciasSemResposta = {};
  DateTime? ultimaRecarga;

  /// Preenchida quando a sessao termina por 401 (token expirado) - a tela de login mostra.
  String? avisoSessao;

  Timer? _recarga;

  Banco(this.api, this.armazenamento) {
    final salva = armazenamento.lerSessao();
    if (salva != null) _usarSessao(salva);
  }

  bool get autenticado => sessao != null;

  String get hostAtual => armazenamento.lerHost() ?? api.config.host;

  void _usarSessao(Sessao s) {
    sessao = s;
    api.config = api.config.comHost(s.host);
    api.token = s.token;
    contas = armazenamento.contas(s.usuario);
  }

  /// Tenta entrar pela agencia escolhida; se ela estiver fora do ar, tenta as outras -
  /// as 3 emitem tokens com a mesma chave, entao qualquer uma serve de porta de entrada.
  Future<void> entrar(String usuario, String senha, int agenciaPreferida, String host) async {
    api.config = api.config.comHost(host.trim().isEmpty ? api.config.host : host.trim());
    await armazenamento.salvarHost(api.config.host);
    api.token = null;
    ErroDeRede? ultimaFalha;
    for (final agencia in [agenciaPreferida, ...ConfigAgencias.todasAsAgencias.where((a) => a != agenciaPreferida)]) {
      try {
        final r = await api.entrar(usuario, senha, agencia);
        final nova = Sessao(
          token: r['token'] as String,
          usuario: r['usuario'] as String,
          nome: r['nome'] as String,
          agenciaEntrada: agencia,
          host: api.config.host,
        );
        await armazenamento.salvarSessao(nova);
        avisoSessao = null;
        _usarSessao(nova);
        notifyListeners();
        return;
      } on ErroDeRede catch (falha) {
        ultimaFalha = falha;
      }
    }
    throw ultimaFalha ?? const ErroDeRede(0, 'nenhuma agencia respondeu');
  }

  Future<void> sair({String? aviso}) async {
    parar();
    sessao = null;
    api.token = null;
    contas = [];
    avisoSessao = aviso;
    await armazenamento.limparSessao();
    notifyListeners();
  }

  void iniciar() {
    _recarga?.cancel();
    recarregarContas();
    _recarga = Timer.periodic(Intervalos.recarregarContas, (_) => recarregarContas());
  }

  void parar() {
    _recarga?.cancel();
    _recarga = null;
  }

  /// 401 em qualquer chamada: token expirado ou invalido. Volta para o login com o motivo.
  Future<T> _comSessao<T>(Future<T> Function() chamada) async {
    try {
      return await chamada();
    } on ErroApi catch (erro) {
      if (erro.status == 401) {
        await sair(aviso: 'Sua sessao expirou (${erro.mensagem}). Entre novamente.');
      }
      rethrow;
    }
  }

  /// Busca as contas do usuario nas 3 agencias (cada uma so conhece as suas).
  Future<void> recarregarContas() async {
    final s = sessao;
    if (s == null) return;
    final semResposta = <int>{};
    await Future.wait(ConfigAgencias.todasAsAgencias.map((agencia) async {
      try {
        final lista = await _comSessao(() => api.minhasContas(agencia));
        for (final json in lista) {
          await armazenamento.salvarConta(Conta.daApi(json as Map<String, dynamic>));
        }
      } on ErroDeRede {
        semResposta.add(agencia);
      } on ErroApi {
        // 401 ja tratado em _comSessao; outros erros nao apagam o cache
      }
    }));
    if (sessao == null) return;
    agenciasSemResposta = semResposta;
    ultimaRecarga = DateTime.now();
    contas = armazenamento.contas(s.usuario);
    notifyListeners();
  }

  Future<void> _guardar(Map<String, dynamic> respostaConta) async {
    await armazenamento.salvarConta(Conta.daApi(respostaConta));
    contas = armazenamento.contas(sessao!.usuario);
    notifyListeners();
  }

  Future<String> abrirConta(int id, String nome, int saldoInicialCentavos) async {
    final r = await _comSessao(() => api.criarConta(id, nome, saldoInicialCentavos / 100));
    await _guardar(r);
    return 'Conta $id aberta na agencia ${ConfigAgencias.agenciaResponsavel(id)}.';
  }

  Future<String> depositar(int id, int centavos) async {
    final r = await _comSessao(() => api.depositar(id, centavos));
    await _guardar(r);
    return 'Deposito de ${reais(centavos)} na conta $id confirmado pela agencia ${ConfigAgencias.agenciaResponsavel(id)}.';
  }

  Future<String> sacar(int id, int centavos) async {
    final r = await _comSessao(() => api.sacar(id, centavos));
    await _guardar(r);
    return 'Saque de ${reais(centavos)} da conta $id confirmado.';
  }

  Future<String> transferir(int origem, int destino, int centavos) async {
    final r = await _comSessao(() => api.transferir(origem, destino, centavos));
    await _guardar(await _comSessao(() => api.conta(origem)));
    if (contas.any((c) => c.id == destino)) unawaited(recarregarContas());
    return r['escopo'] == 'LOCAL'
        ? 'Transferencia de ${reais(centavos)} concluida (mesma agencia).'
        : 'Transferencia de ${reais(centavos)} publicada para a agencia ${r['agenciaDestino']} (entrega assincrona).';
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
