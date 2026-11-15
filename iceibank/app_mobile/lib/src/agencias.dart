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

/// Funcionalidade adicional do Sprint 3: quais das 3 agencias estao respondendo agora,
/// nao so quem e a lider. Consulta `GET /status` (publica) das 3 em paralelo a cada 5 s.
///
/// O painel da lider responde "quem coordena"; este responde "quem esta vivo". Juntos
/// mostram, por exemplo, a lider caindo (a 2 fica vermelha) e, segundos depois, o painel
/// da lider trocando para a 1 - o heartbeat em acao, visto de fora.
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
      final status = await _api.status(id);
      return SituacaoAgencia(
        id: id,
        noAr: true,
        latenciaMs: relogio.elapsedMilliseconds,
        vetor: (status['relogioVetorial'] as List?)?.map((v) => (v as num).toInt()).toList(),
        contas: (status['quantidadeContas'] as num?)?.toInt(),
        verificadaEm: DateTime.now(),
      );
    } on ErroDeRede {
      return SituacaoAgencia(id: id, noAr: false, verificadaEm: DateTime.now());
    } on ErroApi {
      return SituacaoAgencia(id: id, noAr: false, verificadaEm: DateTime.now());
    }
  }

  @override
  void dispose() {
    parar();
    super.dispose();
  }
}
