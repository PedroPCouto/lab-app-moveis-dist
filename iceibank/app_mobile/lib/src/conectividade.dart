import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

/// Parte F: sabe se o aparelho tem rede (Wi-Fi, dados moveis...) e avisa quando ela volta.
/// No Android vem do ConnectivityManager; no navegador, do navigator.onLine / eventos
/// online-offline - os dois pelo mesmo pacote connectivity_plus.
///
/// "Ter rede" nao e "a agencia responde": a agencia pode estar fora do ar com o Wi-Fi
/// ligado. Por isso o app tambem guarda a operacao quando a chamada falha por rede
/// (ErroDeRede) e tenta sincronizar periodicamente, nao so quando este sinal muda.
class Conectividade extends ChangeNotifier {
  final Connectivity _connectivity;
  StreamSubscription<List<ConnectivityResult>>? _assinatura;
  bool online = true;
  DateTime? mudouEm;

  /// Chamado na transicao offline -> online (gatilho da sincronizacao automatica).
  final List<VoidCallback> _aoReconectar = [];

  Conectividade([Connectivity? connectivity]) : _connectivity = connectivity ?? Connectivity();

  Future<void> iniciar() async {
    try {
      _definir(_temRede(await _connectivity.checkConnectivity()));
    } catch (_) {
      // plataforma sem suporte: assume online e deixa os erros de rede decidirem
    }
    _assinatura = _connectivity.onConnectivityChanged.listen((r) => _definir(_temRede(r)));
  }

  void aoReconectar(VoidCallback acao) => _aoReconectar.add(acao);

  static bool _temRede(List<ConnectivityResult> resultados) => resultados.any((r) => r != ConnectivityResult.none);

  void _definir(bool agora) {
    if (agora == online) return;
    online = agora;
    mudouEm = DateTime.now();
    notifyListeners();
    if (agora) {
      for (final acao in _aoReconectar) {
        acao();
      }
    }
  }

  @override
  void dispose() {
    _assinatura?.cancel();
    super.dispose();
  }
}
