import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_app/src/api.dart';
import 'package:iceibank_app/src/armazenamento.dart';
import 'package:iceibank_app/src/banco.dart';
import 'package:iceibank_app/src/conectividade.dart';
import 'package:iceibank_app/src/config.dart';
import 'package:iceibank_app/src/modelos.dart';

/// Agencia 0 de mentira, com as mesmas regras do backend para a conta 30: vetor da
/// agencia, comparacao no /sincronizar, 409 com contaAtual.
class AgenciaFalsa {
  int saldoCentavos = 10000;
  List<int> vetor = [5, 0, 0];
  bool noAr = true;
  final recebidas = <Map<String, dynamic>>[];

  Map<String, dynamic> get conta => {
        'id': 30,
        'nomeAluno': 'Ana',
        'dono': 'ana',
        'saldo': saldoCentavos / 100,
        'vetorAtual': List<int>.from(vetor),
      };

  void eventoLocal() => vetor = [vetor[0] + 1, vetor[1], vetor[2]];

  Future<http.Response> responder(http.Request req) async {
    if (!noAr) throw http.ClientException('Connection refused');
    final porta = req.url.port;
    if (porta != 4000) return http.Response('[]', 200);
    final caminho = req.url.path;
    http.Response json(Object corpo, [int status = 200]) =>
        http.Response(jsonEncode(corpo), status, headers: {'content-type': 'application/json'});
    if (req.method == 'GET' && caminho == '/contas') return json([conta]);
    if (req.method == 'GET' && caminho == '/contas/30') return json(conta);
    if (req.method == 'POST' && caminho == '/contas/30/depositar') {
      eventoLocal();
      saldoCentavos += ((jsonDecode(req.body)['valor'] as num) * 100).round();
      return json(conta);
    }
    if (req.method == 'POST' && caminho == '/contas/30/sincronizar') {
      final corpo = jsonDecode(req.body) as Map<String, dynamic>;
      recebidas.add(corpo);
      final conhecido = (corpo['vetorConhecido'] as List).cast<int>();
      if (conhecido.join(',') != vetor.join(',')) {
        return json({'erro': 'Conflito', 'vetorConhecido': conhecido, 'vetorAtual': vetor, 'contaAtual': conta}, 409);
      }
      final valor = ((corpo['valor'] as num) * 100).round();
      if (corpo['tipo'] == 'SAQUE' && saldoCentavos < valor) return json({'erro': 'Saldo insuficiente.'}, 400);
      eventoLocal();
      saldoCentavos += corpo['tipo'] == 'DEPOSITO' ? valor : -valor;
      return json({'mensagem': 'ok', 'idOperacao': corpo['idOperacao'], 'duplicada': false, 'conta': conta});
    }
    return json({'erro': 'rota nao simulada: ${req.method} $caminho'}, 404);
  }
}

void main() {
  late Directory pasta;
  late AgenciaFalsa agencia;
  late Banco banco;

  setUp(() async {
    pasta = await Directory.systemTemp.createTemp('iceibank_teste');
    agencia = AgenciaFalsa();
    final armazenamento = await Armazenamento.abrirEm(pasta.path);
    await armazenamento.salvarSessao(
        const Sessao(token: 't', usuario: 'ana', nome: 'Ana', agenciaEntrada: 0, host: 'localhost'));
    final api = ApiCliente(const ConfigAgencias(host: 'localhost'), cliente: MockClient(agencia.responder));
    banco = Banco(api, armazenamento, Conectividade());
    await banco.recarregarContas(); // o app "ja leu" a conta 30 uma vez, online
  });

  tearDown(() async {
    banco.parar();
    await Hive.close();
    await pasta.delete(recursive: true);
  });

  Conta conta30() => banco.contas.singleWhere((c) => c.id == 30);

  void ficarOffline() => banco.conectividade.online = false;
  void voltarOnline() => banco.conectividade.online = true;

  test('a conta lida online fica no armazenamento local com saldo e vetor', () {
    expect(conta30().saldoCentavos, 10000);
    expect(conta30().vetorAtual, [5, 0, 0]);
  });

  test('offline: deposito vira pendente, nada e enviado e o saldo provisorio ja reflete', () async {
    ficarOffline();

    final msg = await banco.depositar(30, 2500);

    expect(msg, contains('PENDENTE'));
    expect(banco.pendentes, hasLength(1));
    expect(banco.pendentes.single.vetorConhecido, [5, 0, 0]);
    expect(banco.saldoProvisorio(conta30()), 12500);
    expect(conta30().saldoCentavos, 10000, reason: 'o confirmado so muda quando a agencia confirmar');
    expect(agencia.recebidas, isEmpty);
  });

  test('ao reconectar, a fila sincroniza sozinha e o saldo confirmado e atualizado', () async {
    ficarOffline();
    await banco.depositar(30, 2500);
    voltarOnline();

    await banco.sincronizar();

    expect(banco.pendentes, isEmpty);
    expect(conta30().saldoCentavos, 12500);
    expect(conta30().vetorAtual, [6, 0, 0]);
  });

  test('duas operacoes offline na mesma conta: a segunda usa o vetor devolvido pela primeira', () async {
    ficarOffline();
    await banco.depositar(30, 1000);
    await banco.sacar(30, 300);
    voltarOnline();

    await banco.sincronizar();

    expect(banco.pendentes, isEmpty);
    expect(agencia.recebidas.map((r) => r['vetorConhecido']), [
      [5, 0, 0],
      [6, 0, 0],
    ]);
    expect(conta30().saldoCentavos, 10700);
  });

  test('409: a operacao vira CONFLITO, o saldo real aparece e a recusa e anunciada', () async {
    ficarOffline();
    await banco.sacar(30, 1000);
    agencia
      ..eventoLocal()
      ..saldoCentavos = 15000; // mudou "por outro caminho" enquanto o app estava offline
    voltarOnline();
    final anunciadas = <List<OperacaoPendente>>[];
    final assinatura = banco.recusas.listen(anunciadas.add);

    await banco.sincronizar();
    await Future<void>.delayed(Duration.zero);

    final op = banco.pendentes.single;
    expect(op.situacao, SituacaoPendente.conflito);
    expect(op.saldoVistoCentavos, 10000);
    expect(op.saldoRealCentavos, 15000);
    expect(conta30().saldoCentavos, 15000);
    expect(banco.saldoProvisorio(conta30()), 15000, reason: 'conflito nao entra no saldo provisorio');
    expect(anunciadas.single.single.idOperacao, op.idOperacao);
    expect(agencia.saldoCentavos, 15000, reason: 'nada foi aplicado as cegas');
    await assinatura.cancel();
  });

  test('depois do conflito, "aplicar sobre o saldo atual" reenvia com o vetor novo', () async {
    ficarOffline();
    await banco.sacar(30, 1000);
    agencia
      ..eventoLocal()
      ..saldoCentavos = 15000;
    voltarOnline();
    await banco.sincronizar();

    await banco.reaplicarSobreSaldoAtual(banco.pendentes.single);

    expect(banco.pendentes, isEmpty);
    expect(agencia.saldoCentavos, 14000);
  });

  test('offline nao deixa sacar mais que o saldo provisorio', () async {
    ficarOffline();
    await banco.sacar(30, 9000);

    expect(() => banco.sacar(30, 2000), throwsA(isA<ErroApi>()));
    expect(banco.pendentes, hasLength(1));
  });

  test('online, mas a agencia nao responde: a operacao tambem vai para a fila', () async {
    agencia.noAr = false;

    final msg = await banco.depositar(30, 500);
    // a tentativa de sincronizar disparada logo apos enfileirar tambem falha por rede
    while (banco.sincronizando) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }

    expect(msg, contains('Agencia 0 nao respondeu'));
    expect(banco.pendentes, hasLength(1));
    expect(banco.pendentes.single.situacao, SituacaoPendente.pendente);
  });

  test('a fila sobrevive a reabrir o armazenamento (o app foi fechado offline)', () async {
    ficarOffline();
    await banco.depositar(30, 700);
    await Hive.close();

    final reaberto = await Armazenamento.abrirEm(pasta.path);

    expect(reaberto.pendentes('ana').single.valorCentavos, 700);
    expect(reaberto.conta('ana', 30)!.vetorAtual, [5, 0, 0]);
  });

  test('formatacao e leitura de valores em reais', () {
    expect(reais(123456), r'R$ 1.234,56');
    expect(centavosDoTexto('25,50'), 2550);
    expect(centavosDoTexto('1.234,56'), 123456);
    expect(centavosDoTexto('30.5'), 3050);
    expect(centavosDoTexto('-3'), isNull);
  });
}
