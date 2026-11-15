import 'package:material_ui/material_ui.dart';

import '../api.dart';
import '../banco.dart';
import '../lideranca.dart';
import '../modelos.dart';
import 'dialogos.dart';
import 'painel_lider.dart';

class TelaInicio extends StatefulWidget {
  final Banco banco;
  final Lideranca lideranca;

  const TelaInicio({super.key, required this.banco, required this.lideranca});

  @override
  State<TelaInicio> createState() => _TelaInicioState();
}

class _TelaInicioState extends State<TelaInicio> {
  Banco get banco => widget.banco;

  @override
  void initState() {
    super.initState();
    banco.iniciar();
    widget.lideranca.iniciar(agenciaPreferida: banco.sessao?.agenciaEntrada ?? 0);
  }

  @override
  void dispose() {
    widget.lideranca.parar();
    banco.parar();
    super.dispose();
  }

  void _mensagem(String texto, {bool erro = false}) {
    if (!mounted) return;
    final tema = Theme.of(context);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(texto),
        backgroundColor: erro ? tema.colorScheme.error : null,
        duration: const Duration(seconds: 5),
      ));
  }

  /// Erros da API aparecem na tela com a mensagem que a agencia devolveu (saldo
  /// insuficiente, conta nao encontrada...). 401 ja levou de volta ao login.
  Future<void> _executar(Future<String> Function() operacao) async {
    try {
      _mensagem(await operacao());
    } on ErroApi catch (e) {
      if (e.status != 401) _mensagem('Erro ${e.status}: ${e.mensagem}', erro: true);
    } on ErroDeRede catch (e) {
      _mensagem(e.toString(), erro: true);
    }
  }

  Future<void> _operar(TipoOperacao tipo, Conta conta) async {
    final dados = await pedirOperacao(context, tipo, conta);
    if (dados == null) return;
    await _executar(() => switch (tipo) {
          TipoOperacao.deposito => banco.depositar(conta.id, dados.valorCentavos),
          TipoOperacao.saque => banco.sacar(conta.id, dados.valorCentavos),
          TipoOperacao.transferencia => banco.transferir(conta.id, dados.idDestino!, dados.valorCentavos),
        });
  }

  Future<void> _abrirConta() async {
    final dados = await pedirNovaConta(context, banco.sessao?.nome ?? '');
    if (dados == null) return;
    await _executar(() => banco.abrirConta(dados.id, dados.nome, dados.saldoInicialCentavos));
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return ListenableBuilder(
      listenable: banco,
      builder: (context, _) {
        final sessao = banco.sessao;
        return Scaffold(
          appBar: AppBar(
            title: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('ICEIBank'),
                if (sessao != null) Text('Ola, ${sessao.nome}', style: tema.textTheme.bodySmall),
              ],
            ),
            actions: [
              IconButton(
                tooltip: 'Sair',
                icon: const Icon(Icons.logout),
                onPressed: () => banco.sair(),
              ),
            ],
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: _abrirConta,
            icon: const Icon(Icons.add),
            label: const Text('Abrir conta'),
          ),
          body: RefreshIndicator(
            onRefresh: banco.recarregarContas,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
              children: [
                PainelLider(lideranca: widget.lideranca),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                  child: Text('Minhas contas', style: tema.textTheme.titleMedium),
                ),
                if (banco.contas.isEmpty)
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('Nenhuma conta ainda. Use "Abrir conta" para criar uma.'),
                    ),
                  ),
                for (final conta in banco.contas)
                  CartaoConta(
                    conta: conta,
                    agenciaSemResposta: banco.agenciasSemResposta.contains(conta.agencia),
                    aoOperar: (tipo) => _operar(tipo, conta),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class CartaoConta extends StatelessWidget {
  final Conta conta;
  final bool agenciaSemResposta;
  final void Function(TipoOperacao) aoOperar;

  const CartaoConta({super.key, required this.conta, required this.agenciaSemResposta, required this.aoOperar});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('Conta ${conta.id} - Agencia ${conta.agencia}', style: tema.textTheme.titleSmall),
                ),
                Text(conta.nomeAluno, style: tema.textTheme.bodySmall),
              ],
            ),
            const SizedBox(height: 8),
            Semantics(
              label: 'Saldo da conta ${conta.id}: ${reais(conta.saldoCentavos)}',
              child: Text(reais(conta.saldoCentavos), style: tema.textTheme.headlineSmall),
            ),
            Text(
              agenciaSemResposta
                  ? 'Agencia ${conta.agencia} sem resposta - ultimo saldo conhecido (${hora(conta.atualizadaEm)})'
                  : 'Confirmado pela agencia as ${hora(conta.atualizadaEm)} - vetor ${vetorTexto(conta.vetorAtual)}',
              style: tema.textTheme.bodySmall?.copyWith(color: agenciaSemResposta ? tema.colorScheme.error : null),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                OutlinedButton(onPressed: () => aoOperar(TipoOperacao.deposito), child: const Text('Depositar')),
                OutlinedButton(onPressed: () => aoOperar(TipoOperacao.saque), child: const Text('Sacar')),
                OutlinedButton(onPressed: () => aoOperar(TipoOperacao.transferencia), child: const Text('Transferir')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
