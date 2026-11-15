import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api.dart';
import 'armazenamento.dart';
import 'config.dart';
import 'modelos.dart';

/// Parte D: descobre qual agencia e a lider consultando `GET /bully/lider`.
///
/// - Consulta a cada [Intervalos.pollingLider] (3 s, o mesmo ritmo do heartbeat das
///   agencias), sem a pessoa usuaria precisar atualizar a tela.
/// - Pergunta a UMA agencia por vez: primeiro a que respondeu da ultima vez; se ela nao
///   responder (fora do ar), tenta as outras antes de desistir.
/// - Se a agencia consultada ainda nao sabe da nova eleicao, o painel mostra o que ela
///   sabe; a proxima consulta (3 s depois) corrige. Nao tenta "votar" entre respostas.
class Lideranca extends ChangeNotifier {
  final ApiCliente _api;
  final Armazenamento _armazenamento;
  Timer? _timer;
  bool _consultando = false;

  int? liderAtual;
  bool eleicaoEmAndamento = false;
  int? informadaPor;
  DateTime? atualizadaEm;
  bool semResposta = false;
  List<int> tentadasNaUltimaConsulta = [];
  int _preferida = 0;

  /// Funcionalidade adicional: trocas de lideranca observadas pelo app (mais recente primeiro).
  List<TrocaDeLider> trocas = [];

  Lideranca(this._api, this._armazenamento) {
    trocas = _armazenamento.trocasDeLider();
  }

  void iniciar({int agenciaPreferida = 0}) {
    _preferida = agenciaPreferida;
    _timer?.cancel();
    consultar();
    _timer = Timer.periodic(Intervalos.pollingLider, (_) => consultar());
  }

  void parar() {
    _timer?.cancel();
    _timer = null;
  }

  List<int> _ordemDeConsulta() {
    final ordem = [_preferida, ...ConfigAgencias.todasAsAgencias.where((a) => a != _preferida)];
    return ordem;
  }

  Future<void> consultar() async {
    if (_consultando) return;
    _consultando = true;
    final tentadas = <int>[];
    try {
      for (final agencia in _ordemDeConsulta()) {
        tentadas.add(agencia);
        try {
          final resposta = await _api.lider(agencia);
          _registrar(agencia, resposta['liderAtual'] as int?);
          return;
        } on ErroDeRede {
          continue; // fora do ar: tenta a proxima
        } on ErroApi {
          continue;
        }
      }
      semResposta = true; // nenhuma das 3 respondeu: mantem a ultima lider conhecida, marcada como antiga
      notifyListeners();
    } finally {
      tentadasNaUltimaConsulta = tentadas;
      _consultando = false;
    }
  }

  void _registrar(int agencia, int? lider) {
    final anterior = liderAtual;
    _preferida = agencia;
    informadaPor = agencia;
    atualizadaEm = DateTime.now();
    semResposta = false;
    eleicaoEmAndamento = lider == null;
    if (lider != null) {
      liderAtual = lider;
      if (lider != anterior) {
        trocas = [
          TrocaDeLider(de: anterior, para: lider, quando: atualizadaEm!, informadaPor: agencia),
          ...trocas,
        ].take(10).toList();
        _armazenamento.salvarTrocasDeLider(trocas);
      }
    }
    notifyListeners();
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
