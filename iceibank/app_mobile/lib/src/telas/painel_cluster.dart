import 'package:material_ui/material_ui.dart';

import '../agencias.dart';
import '../config.dart';
import '../lideranca.dart';
import '../modelos.dart';

/// Funcionalidade adicional: agencias ativas + historico das trocas de lideranca vistas
/// pelo app.
class PainelCluster extends StatelessWidget {
  final SaudeAgencias saude;
  final Lideranca lideranca;

  const PainelCluster({super.key, required this.saude, required this.lideranca});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([saude, lideranca]),
      builder: (context, _) {
        final ativas = saude.situacoes.values.where((s) => s.noAr).length;
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'AGENCIAS DO CLUSTER - ${saude.situacoes.isEmpty ? '...' : '$ativas de ${ConfigAgencias.numeroAgencias} respondendo'}',
                  style: tema.textTheme.labelMedium,
                ),
                const SizedBox(height: 8),
                for (final id in ConfigAgencias.todasAsAgencias) _linha(tema, saude.situacoes[id], id),
                const Divider(height: 20),
                Text('ULTIMAS TROCAS DE LIDERANCA (vistas por este app)', style: tema.textTheme.labelMedium),
                const SizedBox(height: 4),
                if (lideranca.trocas.isEmpty) Text('Nenhuma ainda.', style: tema.textTheme.bodySmall),
                for (final troca in lideranca.trocas.take(4))
                  Semantics(
                    label:
                        'Troca de lider: ${troca.de == null ? 'inicio' : 'agencia ${troca.de}'} para agencia ${troca.para}',
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Text(
                        '${hora(troca.quando)}  ${troca.de == null ? 'lider inicial' : 'agencia ${troca.de}'}  ->  '
                        'agencia ${troca.para}  (informado pela ${troca.informadaPor})',
                        style: tema.textTheme.bodySmall,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _linha(ThemeData tema, SituacaoAgencia? s, int id) {
    final lider = lideranca.liderAtual == id;
    final noAr = s?.noAr ?? false;
    final cor = s == null ? Colors.grey : (noAr ? Colors.green.shade700 : tema.colorScheme.error);
    final detalhe = s == null
        ? 'verificando...'
        : noAr
        ? 'no ar - ${s.latenciaMs} ms - ${s.contas} contas - vetor ${vetorTexto(s.vetor ?? const [])}'
        : 'FORA DO AR (sem resposta as ${hora(s.verificadaEm)})';
    return Semantics(
      label: 'Agencia $id: ${s == null ? 'verificando' : (noAr ? 'no ar' : 'fora do ar')}${lider ? ', lider' : ''}',
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          children: [
            Icon(noAr ? Icons.circle : Icons.cancel, color: cor, size: 14),
            const SizedBox(width: 8),
            Text('Agencia $id', style: tema.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
            if (lider) ...[
              const SizedBox(width: 4),
              Icon(Icons.workspace_premium, size: 16, color: tema.colorScheme.primary),
            ],
            const SizedBox(width: 8),
            Expanded(
              child: Text(detalhe, style: tema.textTheme.bodySmall?.copyWith(color: noAr ? null : cor)),
            ),
          ],
        ),
      ),
    );
  }
}
