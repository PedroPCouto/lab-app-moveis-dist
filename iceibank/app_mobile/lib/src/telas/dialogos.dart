import 'package:material_ui/material_ui.dart';

import '../config.dart';
import '../modelos.dart';

class DadosOperacao {
  final int valorCentavos;
  final int? idDestino;

  const DadosOperacao(this.valorCentavos, [this.idDestino]);
}

/// Formulario de deposito, saque ou transferencia. Devolve null se cancelado.
Future<DadosOperacao?> pedirOperacao(BuildContext context, TipoOperacao tipo, Conta conta) {
  final valor = TextEditingController();
  final destino = TextEditingController();
  String? erro;
  return showDialog<DadosOperacao>(
    context: context,
    builder: (context) => StatefulBuilder(
      builder: (context, setState) {
        void confirmar() {
          final centavos = centavosDoTexto(valor.text);
          final idDestino = int.tryParse(destino.text.trim());
          if (centavos == null) {
            setState(() => erro = 'Informe um valor maior que zero.');
            return;
          }
          if (tipo == TipoOperacao.transferencia && idDestino == null) {
            setState(() => erro = 'Informe a conta de destino.');
            return;
          }
          Navigator.of(context).pop(DadosOperacao(centavos, idDestino));
        }

        return AlertDialog(
          title: Text('${tipo.rotulo} - conta ${conta.id}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (tipo == TipoOperacao.transferencia) ...[
                TextField(
                  controller: destino,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: 'Conta de destino',
                    helperText: destino.text.isEmpty
                        ? 'A conta N fica na agencia N % 3'
                        : _escopo(conta.id, int.tryParse(destino.text) ?? 0),
                  ),
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: 8),
              ],
              TextField(
                controller: valor,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Valor (R\$)'),
                onSubmitted: (_) => confirmar(),
              ),
              if (erro != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(erro!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
            FilledButton(onPressed: confirmar, child: const Text('Confirmar')),
          ],
        );
      },
    ),
  );
}

String _escopo(int origem, int destino) =>
    ConfigAgencias.agenciaResponsavel(origem) == ConfigAgencias.agenciaResponsavel(destino)
    ? 'Mesma agencia (${ConfigAgencias.agenciaResponsavel(destino)}): credito imediato'
    : 'Outra agencia (${ConfigAgencias.agenciaResponsavel(destino)}): credito via RabbitMQ';

class DadosNovaConta {
  final int id;
  final String nome;
  final int saldoInicialCentavos;

  const DadosNovaConta(this.id, this.nome, this.saldoInicialCentavos);
}

Future<DadosNovaConta?> pedirNovaConta(BuildContext context, String nomePadrao) {
  final id = TextEditingController();
  final nome = TextEditingController(text: nomePadrao);
  final saldo = TextEditingController(text: '100');
  String? erro;
  return showDialog<DadosNovaConta>(
    context: context,
    builder: (context) => StatefulBuilder(
      builder: (context, setState) => AlertDialog(
        title: const Text('Abrir conta'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: id,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: 'Numero da conta',
                helperText: int.tryParse(id.text) == null
                    ? 'Define a agencia: N % 3'
                    : 'Sera aberta na agencia ${ConfigAgencias.agenciaResponsavel(int.parse(id.text))}',
              ),
              onChanged: (_) => setState(() {}),
            ),
            TextField(
              controller: nome,
              decoration: const InputDecoration(labelText: 'Nome do titular'),
            ),
            TextField(
              controller: saldo,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'Saldo inicial (R\$)'),
            ),
            if (erro != null) Text(erro!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
          FilledButton(
            onPressed: () {
              final numero = int.tryParse(id.text.trim());
              final inicial = saldo.text.trim().isEmpty ? 0 : centavosDoTexto(saldo.text);
              if (numero == null || numero < 0 || inicial == null) {
                setState(() => erro = 'Informe numero e saldo validos.');
                return;
              }
              Navigator.of(context).pop(DadosNovaConta(numero, nome.text.trim(), inicial));
            },
            child: const Text('Abrir'),
          ),
        ],
      ),
    ),
  );
}
