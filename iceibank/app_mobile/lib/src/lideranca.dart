import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api.dart';
import 'armazenamento.dart';
import 'config.dart';
import 'modelos.dart';

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
          continue;
        } on ErroApi {
          continue;
        }
      }
      semResposta = true;
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
