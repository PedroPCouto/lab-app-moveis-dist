import 'dart:async';

import 'package:material_ui/material_ui.dart';

import '../api.dart';
import '../banco.dart';
import '../lideranca.dart';
import '../modelos.dart';
import 'dialogos.dart';
import 'painel_lider.dart';
import 'pendentes.dart';

class TelaInicio extends StatefulWidget {
  final Banco banco;
  final Lideranca lideranca;

  const TelaInicio({super.key, required this.banco, required this.lideranca});

  @override
  State<TelaInicio> createState() => _TelaInicioState();
}

class _TelaInicioState extends State<TelaInicio> {
  Banco get banco => widget.banco;
  final _assinaturas = <StreamSubscription<dynamic>>[];

  @override
  void initState() {
    super.initState();
    _assinaturas.add(banco.avisos.listen(_mensagem));
    // Parte F: recusa na sincronizacao (409) abre um dialogo - nunca fica silenciosa.
    _assinaturas.add(
      banco.recusas.listen((ops) {
        if (mounted) mostrarRecusas(context, banco, ops);
      }),
    );
    banco.iniciar();
    widget.lideranca.iniciar(agenciaPreferida: banco.sessao?.agenciaEntrada ?? 0);
  }

  @override
  void dispose() {
    for (final a in _assinaturas) {
      a.cancel();
    }
    widget.lideranca.parar();
    banco.parar();
    super.dispose();
  }

  void _mensagem(String texto, {bool erro = false}) {
    if (!mounted) return;
    final tema = Theme.of(context);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(texto),
          backgroundColor: erro ? tema.colorScheme.error : null,
          duration: const Duration(seconds: 5),
        ),
      );
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
    await _executar(
      () => switch (tipo) {
        TipoOperacao.deposito => banco.depositar(conta.id, dados.valorCentavos),
        TipoOperacao.saque => banco.sacar(conta.id, dados.valorCentavos),
        TipoOperacao.transferencia => banco.transferir(conta.id, dados.idDestino!, dados.valorCentavos),
      },
    );
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
              Semantics(
                label: banco.online ? 'Conexao: online' : 'Conexao: offline',
                child: Chip(
                  avatar: Icon(banco.online ? Icons.wifi : Icons.wifi_off, size: 16),
                  label: Text(banco.online ? 'Online' : 'Offline'),
                  backgroundColor: banco.online ? null : tema.colorScheme.errorContainer,
                  visualDensity: VisualDensity.compact,
                ),
              ),
              IconButton(tooltip: 'Sair', icon: const Icon(Icons.logout), onPressed: () => banco.sair()),
            ],
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: _abrirConta,
            icon: const Icon(Icons.add),
            label: const Text('Abrir conta'),
          ),
          body: Column(
            children: [
              FaixaConexao(banco: banco),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: banco.recarregarContas,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
                    children: [
                      CartaoRecusadas(banco: banco),
                      // a fila vem antes de tudo: e o que ainda nao aconteceu de verdade
                      ListaPendentes(banco: banco),
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
                          online: banco.online,
                          pendentes: banco.pendentesDa(conta.id).length,
                          saldoProvisorio: banco.saldoProvisorio(conta),
                          aoOperar: (tipo) => _operar(tipo, conta),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class CartaoConta extends StatelessWidget {
  final Conta conta;
  final bool agenciaSemResposta;
  final bool online;
  final int pendentes;
  final int saldoProvisorio;
  final void Function(TipoOperacao) aoOperar;

  const CartaoConta({
    super.key,
    required this.conta,
    required this.agenciaSemResposta,
    required this.online,
    required this.pendentes,
    required this.saldoProvisorio,
    required this.aoOperar,
  });

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
                Expanded(child: Text('Conta ${conta.id} - Agencia ${conta.agencia}', style: tema.textTheme.titleSmall)),
                Text(conta.nomeAluno, style: tema.textTheme.bodySmall),
              ],
            ),
            const SizedBox(height: 8),
            if (pendentes > 0) ...[
              // UI otimista: o efeito esperado ja aparece, marcado como provisorio
              Semantics(
                label: 'Saldo provisorio da conta ${conta.id}: ${reais(saldoProvisorio)}',
                child: Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 8,
                  children: [
                    Text(
                      reais(saldoProvisorio),
                      style: tema.textTheme.headlineSmall?.copyWith(color: Colors.orange.shade900),
                    ),
                    Chip(
                      avatar: Icon(Icons.schedule, size: 16, color: Colors.orange.shade900),
                      label: Text('PROVISORIO - $pendentes pendente(s)'),
                      visualDensity: VisualDensity.compact,
                    ),
                  ],
                ),
              ),
              Text(
                'Ultimo saldo confirmado pela agencia: ${reais(conta.saldoCentavos)}',
                style: tema.textTheme.bodySmall,
              ),
            ] else
              Semantics(
                label: 'Saldo da conta ${conta.id}: ${reais(conta.saldoCentavos)}',
                child: Text(reais(conta.saldoCentavos), style: tema.textTheme.headlineSmall),
              ),
            Text(
              !online || agenciaSemResposta
                  ? '${online ? 'Agencia ${conta.agencia} sem resposta' : 'Offline'} - dados guardados no aparelho '
                        '(${hora(conta.atualizadaEm)}) - vetor ${vetorTexto(conta.vetorAtual)}'
                  : 'Confirmado pela agencia as ${hora(conta.atualizadaEm)} - vetor ${vetorTexto(conta.vetorAtual)}',
              style: tema.textTheme.bodySmall?.copyWith(
                color: !online || agenciaSemResposta ? tema.colorScheme.error : null,
              ),
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
