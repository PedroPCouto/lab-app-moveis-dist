import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

class Conectividade extends ChangeNotifier {
  final Connectivity _connectivity;
  StreamSubscription<List<ConnectivityResult>>? _assinatura;
  bool online = true;
  DateTime? mudouEm;

  final List<VoidCallback> _aoReconectar = [];

  Conectividade([Connectivity? connectivity]) : _connectivity = connectivity ?? Connectivity();

  Future<void> iniciar() async {
    try {
      _definir(_temRede(await _connectivity.checkConnectivity()));
    } catch (_) {}
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
