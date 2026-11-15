import 'package:flutter/foundation.dart';
import 'package:flutter/semantics.dart';
import 'package:material_ui/material_ui.dart';

import 'src/api.dart';
import 'src/app.dart';
import 'src/armazenamento.dart';
import 'src/banco.dart';
import 'src/config.dart';
import 'src/lideranca.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (kIsWeb) {
    // No navegador, liga a arvore de acessibilidade: leitores de tela (e os testes
    // Playwright das evidencias) enxergam campos e botoes pelos rotulos.
    SemanticsBinding.instance.ensureSemantics();
  }

  final armazenamento = await Armazenamento.abrir();
  final config = ConfigAgencias.padrao();
  final api = ApiCliente(config.comHost(armazenamento.lerHost() ?? config.host));
  final banco = Banco(api, armazenamento);
  final lideranca = Lideranca(api, armazenamento);

  runApp(IceibankApp(banco: banco, lideranca: lideranca));
}
