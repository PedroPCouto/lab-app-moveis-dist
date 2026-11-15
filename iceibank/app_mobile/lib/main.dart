import 'package:flutter/foundation.dart';
import 'package:flutter/semantics.dart';
import 'package:material_ui/material_ui.dart';

import 'src/agencias.dart';
import 'src/api.dart';
import 'src/app.dart';
import 'src/armazenamento.dart';
import 'src/banco.dart';
import 'src/conectividade.dart';
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
  final conectividade = Conectividade();
  await conectividade.iniciar();
  final banco = Banco(api, armazenamento, conectividade);
  final lideranca = Lideranca(api, armazenamento);
  // rede de volta: o painel da lider tambem nao espera o proximo ciclo de 3 s
  conectividade.aoReconectar(lideranca.consultar);

  final saude = SaudeAgencias(api);

  runApp(IceibankApp(banco: banco, lideranca: lideranca, saude: saude));
}
