import 'package:material_ui/material_ui.dart';

import 'banco.dart';
import 'lideranca.dart';
import 'telas/tela_inicio.dart';
import 'telas/tela_login.dart';

class IceibankApp extends StatelessWidget {
  final Banco banco;
  final Lideranca lideranca;

  const IceibankApp({super.key, required this.banco, required this.lideranca});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICEIBank',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1F4E8C)),
        useMaterial3: true,
      ),
      // Sem sessao -> login; com sessao -> tela inicial. Um 401 em qualquer ponto chama
      // banco.sair(), e esta troca acontece sozinha.
      home: ListenableBuilder(
        listenable: banco,
        builder: (context, _) => banco.autenticado
            ? TelaInicio(banco: banco, lideranca: lideranca)
            : TelaLogin(banco: banco),
      ),
    );
  }
}
