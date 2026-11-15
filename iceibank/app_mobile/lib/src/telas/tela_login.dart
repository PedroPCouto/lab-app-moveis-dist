import 'package:material_ui/material_ui.dart';

import '../api.dart';
import '../banco.dart';
import '../config.dart';

class TelaLogin extends StatefulWidget {
  final Banco banco;

  const TelaLogin({super.key, required this.banco});

  @override
  State<TelaLogin> createState() => _TelaLoginState();
}

class _TelaLoginState extends State<TelaLogin> {
  final _usuario = TextEditingController();
  final _senha = TextEditingController();
  late final TextEditingController _host = TextEditingController(text: widget.banco.hostAtual);
  int _agencia = 0;
  bool _entrando = false;
  String? _erro;

  @override
  void dispose() {
    _usuario.dispose();
    _senha.dispose();
    _host.dispose();
    super.dispose();
  }

  Future<void> _entrar() async {
    setState(() {
      _entrando = true;
      _erro = null;
    });
    try {
      await widget.banco.entrar(_usuario.text.trim(), _senha.text, _agencia, _host.text);
    } on ErroApi catch (e) {
      setState(() => _erro = e.mensagem);
    } on ErroDeRede catch (e) {
      setState(() => _erro = 'Nenhuma agencia respondeu em ${_host.text}. ${e.detalhe}');
    } finally {
      if (mounted) setState(() => _entrando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final aviso = widget.banco.avisoSessao;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Icon(Icons.account_balance, size: 56, color: tema.colorScheme.primary),
                  const SizedBox(height: 8),
                  Text('ICEIBank', textAlign: TextAlign.center, style: tema.textTheme.headlineMedium),
                  Text('App mobile - Sprint 3', textAlign: TextAlign.center, style: tema.textTheme.bodyMedium),
                  const SizedBox(height: 24),
                  if (aviso != null)
                    Card(
                      color: tema.colorScheme.errorContainer,
                      child: Padding(padding: const EdgeInsets.all(12), child: Text(aviso)),
                    ),
                  TextField(
                    controller: _usuario,
                    decoration: const InputDecoration(labelText: 'Usuario', border: OutlineInputBorder()),
                    textInputAction: TextInputAction.next,
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _senha,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Senha', border: OutlineInputBorder()),
                    onSubmitted: (_) => _entrar(),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    initialValue: _agencia,
                    decoration: const InputDecoration(labelText: 'Agencia de entrada', border: OutlineInputBorder()),
                    items: [
                      for (final a in ConfigAgencias.todasAsAgencias)
                        DropdownMenuItem(value: a, child: Text('Agencia $a')),
                    ],
                    onChanged: (v) => setState(() => _agencia = v ?? 0),
                  ),
                  const SizedBox(height: 4),
                  ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    title: const Text('Servidor das agencias'),
                    subtitle: Text('${_host.text}:${widget.banco.api.config.porta(0)}-${widget.banco.api.config.porta(2)}'),
                    children: [
                      TextField(
                        controller: _host,
                        decoration: const InputDecoration(
                          labelText: 'Host',
                          helperText: 'Emulador Android: 10.0.2.2. Celular: IP do PC na rede.',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                  ),
                  if (_erro != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(_erro!, style: TextStyle(color: tema.colorScheme.error)),
                    ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _entrando ? null : _entrar,
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text(_entrando ? 'Entrando...' : 'Entrar'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
