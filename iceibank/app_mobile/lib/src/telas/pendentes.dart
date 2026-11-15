import 'package:material_ui/material_ui.dart';

import '../banco.dart';
import '../modelos.dart';

/// Faixa no topo da tela: sem rede, ou sincronizando a fila.
class FaixaConexao extends StatelessWidget {
  final Banco banco;

  const FaixaConexao({super.key, required this.banco});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final naFila = banco.pendentes.where((p) => p.situacao == SituacaoPendente.pendente).length;
    if (!banco.online) {
      return _faixa(
        tema,
        cor: Colors.grey.shade800,
        icone: Icons.wifi_off,
        texto:
            'Voce esta offline. Saldos sao os ultimos confirmados; operacoes ficam PENDENTES '
            'e serao sincronizadas sozinhas quando a conexao voltar.'
            '${naFila > 0 ? ' ($naFila na fila)' : ''}',
      );
    }
    if (banco.sincronizando) {
      return _faixa(
        tema,
        cor: tema.colorScheme.primary,
        icone: Icons.sync,
        texto: 'Conexao de volta: sincronizando $naFila operacao(oes) pendente(s)...',
      );
    }
    if (naFila > 0) {
      return _faixa(
        tema,
        cor: Colors.orange.shade800,
        icone: Icons.schedule,
        texto: '$naFila operacao(oes) aguardando a agencia responder. Nova tentativa automatica a cada 4 s.',
      );
    }
    return const SizedBox.shrink();
  }

  Widget _faixa(ThemeData tema, {required Color cor, required IconData icone, required String texto}) => Container(
    width: double.infinity,
    color: cor,
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
    child: Row(
      children: [
        Icon(icone, color: Colors.white, size: 20),
        const SizedBox(width: 10),
        Expanded(
          child: Text(texto, style: const TextStyle(color: Colors.white, fontSize: 13)),
        ),
      ],
    ),
  );
}

/// Operacoes que a agencia recusou: ficam na tela ate a pessoa decidir.
class CartaoRecusadas extends StatelessWidget {
  final Banco banco;

  const CartaoRecusadas({super.key, required this.banco});

  @override
  Widget build(BuildContext context) {
    final recusadas = banco.recusadas;
    if (recusadas.isEmpty) return const SizedBox.shrink();
    final tema = Theme.of(context);
    return Card(
      color: tema.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.report, color: tema.colorScheme.error),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Sincronizacao recusada (${recusadas.length})',
                    style: tema.textTheme.titleSmall?.copyWith(color: tema.colorScheme.onErrorContainer),
                  ),
                ),
              ],
            ),
            for (final op in recusadas) ...[const Divider(), ItemRecusada(banco: banco, op: op)],
          ],
        ),
      ),
    );
  }
}

class ItemRecusada extends StatelessWidget {
  final Banco banco;
  final OperacaoPendente op;
  final VoidCallback? depoisDeDecidir;

  const ItemRecusada({super.key, required this.banco, required this.op, this.depoisDeDecidir});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final conflito = op.situacao == SituacaoPendente.conflito;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${op.descricao} (feita offline as ${hora(op.criadaEm)}) - NAO APLICADA',
          style: tema.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
        ),
        if (op.mensagem != null) Text(op.mensagem!, style: tema.textTheme.bodySmall),
        if (conflito && op.saldoRealCentavos != null)
          Text(
            'Saldo que voce via: ${reais(op.saldoVistoCentavos)}  ->  saldo real agora: ${reais(op.saldoRealCentavos!)}',
            style: tema.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600),
          ),
        Wrap(
          spacing: 8,
          children: [
            if (conflito)
              FilledButton.tonal(
                onPressed: () {
                  depoisDeDecidir?.call();
                  banco.reaplicarSobreSaldoAtual(op);
                },
                child: const Text('Aplicar sobre o saldo atual'),
              ),
            TextButton(
              onPressed: () {
                depoisDeDecidir?.call();
                banco.descartar(op);
              },
              child: const Text('Descartar'),
            ),
          ],
        ),
      ],
    );
  }
}

/// Fila de operacoes feitas offline, ainda nao enviadas.
class ListaPendentes extends StatelessWidget {
  final Banco banco;

  const ListaPendentes({super.key, required this.banco});

  @override
  Widget build(BuildContext context) {
    final fila = banco.pendentes.where((p) => p.situacao == SituacaoPendente.pendente).toList();
    if (fila.isEmpty) return const SizedBox.shrink();
    final tema = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
              child: Text('Operacoes pendentes de sincronizacao', style: tema.textTheme.titleSmall),
            ),
            for (final op in fila)
              ListTile(
                dense: true,
                leading: Icon(banco.sincronizando ? Icons.sync : Icons.schedule, color: Colors.orange.shade800),
                title: Text(op.descricao),
                subtitle: Text(
                  'PENDENTE - feita offline as ${hora(op.criadaEm)} - vetor conhecido ${vetorTexto(op.vetorConhecido)}',
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Dialogo aberto sozinho quando a agencia recusa uma sincronizacao: o conflito nunca e
/// silencioso.
Future<void> mostrarRecusas(BuildContext context, Banco banco, List<OperacaoPendente> recusadas) {
  return showDialog<void>(
    context: context,
    builder: (contexto) => AlertDialog(
      icon: Icon(Icons.sync_problem, color: Theme.of(contexto).colorScheme.error, size: 36),
      title: const Text('A agencia recusou a sincronizacao'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enquanto voce estava offline, a conta mudou por outro caminho. Para nao aplicar '
              'uma operacao sobre um saldo que voce nao viu, a agencia devolveu o estado real '
              '(HTTP 409). Nada foi aplicado. Escolha o que fazer:',
            ),
            for (final op in recusadas) ...[
              const Divider(),
              ItemRecusada(banco: banco, op: op, depoisDeDecidir: () => Navigator.of(contexto).pop()),
            ],
          ],
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.of(contexto).pop(), child: const Text('Decidir depois'))],
    ),
  );
}
