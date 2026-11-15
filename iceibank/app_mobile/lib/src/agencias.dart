import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api.dart';
import 'config.dart';

class SituacaoAgencia {
  final int id;
  final bool noAr;
  final int? latenciaMs;
  final List<int>? vetor;
  final int? contas;
  final DateTime verificadaEm;

  const SituacaoAgencia({
    required this.id,
    required this.noAr,
    this.latenciaMs,
    this.vetor,
    this.contas,
    required this.verificadaEm,
  });
}

class SaudeAgencias extends ChangeNotifier {
  final ApiCliente _api;
  Timer? _timer;
  Map<int, SituacaoAgencia> situacoes = {};

  SaudeAgencias(this._api);

  void iniciar() {
    _timer?.cancel();
    verificar();
    _timer = Timer.periodic(Intervalos.pollingAgencias, (_) => verificar());
  }

  void parar() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> verificar() async {
    final resultados = await Future.wait(ConfigAgencias.todasAsAgencias.map(_verificar));
    situacoes = {for (final s in resultados) s.id: s};
    notifyListeners();
  }

  Future<SituacaoAgencia> _verificar(int id) async {
    final relogio = Stopwatch()..start();
    try {
      await _api.lider(id);
    } on ErroDeRede {
      return SituacaoAgencia(id: id, noAr: false, verificadaEm: DateTime.now());
    } on ErroApi {
      return SituacaoAgencia(id: id, noAr: false, verificadaEm: DateTime.now());
    }
    final latencia = relogio.elapsedMilliseconds;
    Map<String, dynamic>? status;
    try {
      status = await _api.status(id);
    } on ErroDeRede {
      status = null;
    } on ErroApi {
      status = null;
    }
    return SituacaoAgencia(
      id: id,
      noAr: true,
      latenciaMs: latencia,
      vetor: (status?['relogioVetorial'] as List?)?.map((v) => (v as num).toInt()).toList(),
      contas: (status?['quantidadeContas'] as num?)?.toInt(),
      verificadaEm: DateTime.now(),
    );
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
