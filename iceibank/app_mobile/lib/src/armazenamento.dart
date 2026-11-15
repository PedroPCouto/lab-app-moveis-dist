import 'dart:convert';

import 'package:hive_ce_flutter/hive_ce_flutter.dart';

import 'modelos.dart';

/// Armazenamento local do app (Parte F), com Hive (hive_ce). No Android grava em arquivos
/// no diretorio do app; no navegador, em IndexedDB - o mesmo codigo nos dois.
///
/// Cada registro e guardado como JSON (String): dispensa gerar TypeAdapters e deixa o
/// formato legivel se for preciso inspecionar.
class Armazenamento {
  static const _caixaSessao = 'sessao';
  static const _caixaContas = 'contas';
  static const _caixaPendentes = 'pendentes';
  static const _caixaLideranca = 'lideranca';

  final Box<String> _sessao;
  final Box<String> _contas;
  final Box<String> _pendentes;
  final Box<String> _lideranca;

  Armazenamento._(this._sessao, this._contas, this._pendentes, this._lideranca);

  static Future<Armazenamento> abrir() async {
    await Hive.initFlutter('iceibank');
    return _abrirCaixas();
  }

  /// Para os testes: Hive numa pasta qualquer, sem path_provider.
  static Future<Armazenamento> abrirEm(String pasta) async {
    Hive.init(pasta);
    return _abrirCaixas();
  }

  static Future<Armazenamento> _abrirCaixas() async {
    return Armazenamento._(
      await Hive.openBox<String>(_caixaSessao),
      await Hive.openBox<String>(_caixaContas),
      await Hive.openBox<String>(_caixaPendentes),
      await Hive.openBox<String>(_caixaLideranca),
    );
  }

  Map<String, dynamic> _ler(String json) => jsonDecode(json) as Map<String, dynamic>;

  // ---- sessao (JWT) ----

  Sessao? lerSessao() {
    final json = _sessao.get('atual');
    return json == null ? null : Sessao.doArmazenamento(_ler(json));
  }

  Future<void> salvarSessao(Sessao sessao) => _sessao.put('atual', jsonEncode(sessao.paraArmazenamento()));

  Future<void> limparSessao() => _sessao.delete('atual');

  String? lerHost() => _sessao.get('host');

  Future<void> salvarHost(String host) => _sessao.put('host', host);

  // ---- contas: ultimo saldo confirmado + vetor, por dono ----

  String _chaveConta(String dono, int id) => '$dono:$id';

  List<Conta> contas(String dono) =>
      _contas.keys
          .cast<String>()
          .where((k) => k.startsWith('$dono:'))
          .map((k) => Conta.doArmazenamento(_ler(_contas.get(k)!)))
          .toList()
        ..sort((a, b) => a.id.compareTo(b.id));

  Conta? conta(String dono, int id) {
    final json = _contas.get(_chaveConta(dono, id));
    return json == null ? null : Conta.doArmazenamento(_ler(json));
  }

  Future<void> salvarConta(Conta conta) =>
      _contas.put(_chaveConta(conta.dono, conta.id), jsonEncode(conta.paraArmazenamento()));

  // ---- fila de operacoes pendentes ----

  List<OperacaoPendente> pendentes(String dono) =>
      _pendentes.values.map((j) => OperacaoPendente.doArmazenamento(_ler(j))).where((p) => p.dono == dono).toList()
        ..sort((a, b) => a.ordem.compareTo(b.ordem));

  Future<void> salvarPendente(OperacaoPendente op) => _pendentes.put(op.idOperacao, jsonEncode(op.paraArmazenamento()));

  Future<void> removerPendente(String idOperacao) => _pendentes.delete(idOperacao);

  int proximaOrdem() {
    final atual = int.tryParse(_sessao.get('ordem') ?? '0') ?? 0;
    _sessao.put('ordem', '${atual + 1}');
    return atual + 1;
  }

  // ---- historico de liderancas observado pelo app (funcionalidade adicional) ----

  List<TrocaDeLider> trocasDeLider() {
    final json = _lideranca.get('trocas');
    if (json == null) return [];
    return (jsonDecode(json) as List).map((t) => TrocaDeLider.doArmazenamento(t as Map<String, dynamic>)).toList();
  }

  Future<void> salvarTrocasDeLider(List<TrocaDeLider> trocas) =>
      _lideranca.put('trocas', jsonEncode(trocas.map((t) => t.paraArmazenamento()).toList()));
}
