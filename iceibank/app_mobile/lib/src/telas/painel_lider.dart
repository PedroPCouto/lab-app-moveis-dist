import 'package:material_ui/material_ui.dart';

import '../lideranca.dart';
import '../modelos.dart';

/// Parte D: o consenso deixa de ser so log de terminal e aparece na tela. Reconstruido a
/// cada consulta (3 s) via ListenableBuilder - nenhum botao de "atualizar".
class PainelLider extends StatelessWidget {
  final Lideranca lideranca;

  const PainelLider({super.key, required this.lideranca});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return ListenableBuilder(
      listenable: lideranca,
      builder: (context, _) {
        final lider = lideranca.liderAtual;
        final String titulo;
        final String detalhe;
        final Color cor;
        final IconData icone;
        if (lideranca.semResposta) {
          titulo = lider == null ? 'Lider desconhecida' : 'Agencia $lider (ultima conhecida)';
          detalhe =
              'Nenhuma agencia respondeu agora (tentadas: ${lideranca.tentadasNaUltimaConsulta.join(', ')}).'
              '${lideranca.atualizadaEm == null ? '' : ' Ultima confirmacao as ${hora(lideranca.atualizadaEm!)}.'}';
          cor = tema.colorScheme.error;
          icone = Icons.cloud_off;
        } else if (lideranca.eleicaoEmAndamento || lider == null) {
          titulo = 'Eleicao em andamento';
          detalhe = lideranca.informadaPor == null
              ? 'Consultando as agencias...'
              : 'A agencia ${lideranca.informadaPor} ainda nao conhece a nova lider.';
          cor = Colors.orange.shade800;
          icone = Icons.how_to_vote;
        } else {
          titulo = 'Agencia $lider';
          detalhe = 'Confirmado pela agencia ${lideranca.informadaPor} as ${hora(lideranca.atualizadaEm!)}';
          cor = tema.colorScheme.primary;
          icone = Icons.workspace_premium;
        }

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('LIDER ATUAL DO CLUSTER', style: tema.textTheme.labelMedium),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(icone, color: cor, size: 36),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Semantics(
                        label: 'Lider atual: $titulo',
                        child: Text(titulo, style: tema.textTheme.headlineSmall?.copyWith(color: cor)),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(detalhe, style: tema.textTheme.bodySmall),
                Text('Atualiza sozinho a cada 3 s (GET /bully/lider).', style: tema.textTheme.bodySmall),
              ],
            ),
          ),
        );
      },
    );
  }
}
