import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';

import 'api.dart';
import 'armazenamento.dart';
import 'conectividade.dart';
import 'config.dart';
import 'modelos.dart';

class Banco extends ChangeNotifier {
  final ApiCliente api;
  final Armazenamento armazenamento;
  final Conectividade conectividade;

  Sessao? sessao;
  List<Conta> contas = [];
  List<OperacaoPendente> pendentes = [];
  bool sincronizando = false;

  Set<int> agenciasSemResposta = {};
  DateTime? ultimaRecarga;

  String? avisoSessao;

  final _avisos = StreamController<String>.broadcast();
  final _recusas = StreamController<List<OperacaoPendente>>.broadcast();

  Stream<String> get avisos => _avisos.stream;

  Stream<List<OperacaoPendente>> get recusas => _recusas.stream;

  Timer? _recarga;
  Timer? _tentativas;
  final _aleatorio = Random.secure();

  Banco(this.api, this.armazenamento, this.conectividade) {
    final salva = armazenamento.lerSessao();
    if (salva != null) _usarSessao(salva);
    conectividade.aoReconectar(() {
      if (sessao == null) return;
      recarregarContas();
      sincronizar();
    });
    conectividade.addListener(notifyListeners);
  }

  bool get autenticado => sessao != null;

  bool get online => conectividade.online;

  String get hostAtual => armazenamento.lerHost() ?? api.config.host;

  void _usarSessao(Sessao s) {
    sessao = s;
    api.config = api.config.comHost(s.host);
    api.token = s.token;
    _relerLocal();
  }

  void _relerLocal() {
    final s = sessao;
    if (s == null) return;
    contas = armazenamento.contas(s.usuario);
    pendentes = armazenamento.pendentes(s.usuario);
  }

  Future<void> entrar(String usuario, String senha, int agenciaPreferida, String host) async {
    api.config = api.config.comHost(host.trim().isEmpty ? api.config.host : host.trim());
    await armazenamento.salvarHost(api.config.host);
    api.token = null;
    ErroDeRede? ultimaFalha;
    for (final agencia in [agenciaPreferida, ...ConfigAgencias.todasAsAgencias.where((a) => a != agenciaPreferida)]) {
      try {
        final r = await api.entrar(usuario, senha, agencia);
        final nova = Sessao(
          token: r['token'] as String,
          usuario: r['usuario'] as String,
          nome: r['nome'] as String,
          agenciaEntrada: agencia,
          host: api.config.host,
        );
        await armazenamento.salvarSessao(nova);
        avisoSessao = null;
        _usarSessao(nova);
        notifyListeners();
        return;
      } on ErroDeRede catch (falha) {
        ultimaFalha = falha;
      }
    }
    throw ultimaFalha ?? const ErroDeRede(0, 'nenhuma agencia respondeu');
  }

  Future<void> sair({String? aviso}) async {
    parar();
    sessao = null;
    api.token = null;
    contas = [];
    pendentes = [];
    avisoSessao = aviso;
    await armazenamento.limparSessao();
    notifyListeners();
  }

  void iniciar() {
    parar();
    recarregarContas().then((_) => sincronizar());
    _recarga = Timer.periodic(Intervalos.recarregarContas, (_) => recarregarContas());
    _tentativas = Timer.periodic(Intervalos.tentarSincronizar, (_) {
      if (online && pendentes.any((p) => p.situacao == SituacaoPendente.pendente)) sincronizar();
    });
  }

  void parar() {
    _recarga?.cancel();
    _tentativas?.cancel();
    _recarga = null;
    _tentativas = null;
  }

  Future<T> _comSessao<T>(Future<T> Function() chamada) async {
    try {
      return await chamada();
    } on ErroApi catch (erro) {
      if (erro.status == 401) {
        await sair(aviso: 'Sua sessao expirou (${erro.mensagem}). Entre novamente.');
      }
      rethrow;
    }
  }

  Future<void> recarregarContas() async {
    final s = sessao;
    if (s == null) return;
    final semResposta = <int>{};
    if (!online) {
      semResposta.addAll(ConfigAgencias.todasAsAgencias);
    } else {
      await Future.wait(
        ConfigAgencias.todasAsAgencias.map((agencia) async {
          try {
            final lista = await _comSessao(() => api.minhasContas(agencia));
            for (final json in lista) {
              await armazenamento.salvarConta(Conta.daApi(json as Map<String, dynamic>));
            }
          } on ErroDeRede {
            semResposta.add(agencia);
          } on ErroApi {
            return;
          }
        }),
      );
    }
    if (sessao == null) return;
    agenciasSemResposta = semResposta;
    ultimaRecarga = DateTime.now();
    _relerLocal();
    notifyListeners();
  }

  List<OperacaoPendente> pendentesDa(int idConta) =>
      pendentes.where((p) => p.idConta == idConta && p.contaNoSaldoProvisorio).toList();

  int saldoProvisorio(Conta conta) =>
      conta.saldoCentavos + pendentesDa(conta.id).fold(0, (soma, p) => soma + p.efeitoCentavos);

  List<OperacaoPendente> get recusadas => pendentes.where((p) => p.situacao != SituacaoPendente.pendente).toList();

  Future<void> _guardar(Map<String, dynamic> respostaConta) async {
    await armazenamento.salvarConta(Conta.daApi(respostaConta));
    _relerLocal();
    notifyListeners();
  }

  Future<String> abrirConta(int id, String nome, int saldoInicialCentavos) async {
    if (!online) throw const ErroApi(0, 'Abrir conta exige conexao com a agencia.');
    final r = await _comSessao(() => api.criarConta(id, nome, saldoInicialCentavos / 100));
    await _guardar(r);
    return 'Conta $id aberta na agencia ${ConfigAgencias.agenciaResponsavel(id)}.';
  }

  Future<String> depositar(int id, int centavos) => _operar(TipoOperacao.deposito, id, centavos, () async {
    await _guardar(await _comSessao(() => api.depositar(id, centavos)));
    return 'Deposito de ${reais(centavos)} na conta $id confirmado pela agencia ${ConfigAgencias.agenciaResponsavel(id)}.';
  });

  Future<String> sacar(int id, int centavos) => _operar(TipoOperacao.saque, id, centavos, () async {
    await _guardar(await _comSessao(() => api.sacar(id, centavos)));
    return 'Saque de ${reais(centavos)} da conta $id confirmado.';
  });

  Future<String> transferir(int origem, int destino, int centavos) => _operar(
    TipoOperacao.transferencia,
    origem,
    centavos,
    destino: destino,
    () async {
      final r = await _comSessao(() => api.transferir(origem, destino, centavos));
      await _guardar(await _comSessao(() => api.conta(origem)));
      if (contas.any((c) => c.id == destino)) unawaited(recarregarContas());
      return r['escopo'] == 'LOCAL'
          ? 'Transferencia de ${reais(centavos)} concluida (mesma agencia).'
          : 'Transferencia de ${reais(centavos)} publicada para a agencia ${r['agenciaDestino']} (entrega assincrona).';
    },
  );

  Future<String> _operar(
    TipoOperacao tipo,
    int idConta,
    int centavos,
    Future<String> Function() online, {
    int? destino,
  }) async {
    final temFila = pendentes.any((p) => p.idConta == idConta && p.situacao == SituacaoPendente.pendente);
    if (!this.online || temFila) {
      return _enfileirar(
        tipo,
        idConta,
        centavos,
        destino,
        motivo: !this.online ? 'Sem conexao' : 'Ha operacoes anteriores desta conta na fila',
      );
    }
    try {
      return await online();
    } on ErroDeRede catch (falha) {
      return _enfileirar(tipo, idConta, centavos, destino, motivo: 'Agencia ${falha.idAgencia} nao respondeu');
    }
  }

  Future<String> _enfileirar(
    TipoOperacao tipo,
    int idConta,
    int centavos,
    int? destino, {
    required String motivo,
  }) async {
    final s = sessao!;
    final conta = armazenamento.conta(s.usuario, idConta);
    if (conta == null) {
      throw ErroApi(
        0,
        'A conta $idConta ainda nao foi consultada neste aparelho: sem dados locais para operar offline.',
      );
    }
    if (tipo != TipoOperacao.deposito && saldoProvisorio(conta) < centavos) {
      throw ErroApi(400, 'Saldo provisorio insuficiente (${reais(saldoProvisorio(conta))}) para operar offline.');
    }
    final op = OperacaoPendente(
      idOperacao: _novoId(),
      dono: s.usuario,
      tipo: tipo,
      idConta: idConta,
      idDestino: destino,
      valorCentavos: centavos,
      vetorConhecido: conta.vetorAtual,
      saldoVistoCentavos: conta.saldoCentavos,
      criadaEm: DateTime.now(),
      ordem: armazenamento.proximaOrdem(),
    );
    await armazenamento.salvarPendente(op);
    _relerLocal();
    notifyListeners();
    if (online) unawaited(sincronizar());
    return '$motivo. ${op.descricao}: guardado no aparelho, PENDENTE de sincronizacao.';
  }

  String _novoId() {
    String hex(int n) => List.generate(n, (_) => _aleatorio.nextInt(16).toRadixString(16)).join();
    return '${hex(8)}-${hex(4)}-4${hex(3)}-${hex(4)}-${hex(12)}';
  }

  Future<void> sincronizar() async {
    final s = sessao;
    if (sincronizando || s == null) return;
    final fila = pendentes.where((p) => p.situacao == SituacaoPendente.pendente).toList();
    if (fila.isEmpty) return;
    sincronizando = true;
    notifyListeners();

    final recusadasAgora = <OperacaoPendente>[];
    var aplicadas = 0;
    try {
      final porConta = <int, List<OperacaoPendente>>{};
      for (final op in fila) {
        porConta.putIfAbsent(op.idConta, () => []).add(op);
      }
      for (final ops in porConta.values) {
        List<int>? baseAceita;
        List<int>? vetorResultante;
        for (var i = 0; i < ops.length; i++) {
          final op = ops[i];
          final vetor = baseAceita != null && listEquals(op.vetorConhecido, baseAceita)
              ? vetorResultante!
              : op.vetorConhecido;
          try {
            final r = await api.sincronizar(op.idConta, op.paraApi(vetor));
            final conta = Conta.daApi(r['conta'] as Map<String, dynamic>);
            await armazenamento.salvarConta(conta);
            await armazenamento.removerPendente(op.idOperacao);
            baseAceita = op.vetorConhecido;
            vetorResultante = conta.vetorAtual;
            aplicadas++;
          } on ErroApi catch (e) {
            if (e.status == 401) {
              await sair(
                aviso:
                    'Sua sessao expirou durante a sincronizacao. Entre de novo: '
                    'as operacoes pendentes continuam guardadas.',
              );
              return;
            }
            if (e.status == 409 && e.corpo?['contaAtual'] != null) {
              final real = Conta.daApi(e.corpo!['contaAtual'] as Map<String, dynamic>);
              await armazenamento.salvarConta(real);
              final vetorAgencia = (e.corpo!['vetorAtual'] as List).map((v) => (v as num).toInt()).toList();
              for (final bloqueada in ops.sublist(i)) {
                final marcada = bloqueada.copiar(
                  situacao: SituacaoPendente.conflito,
                  mensagem: identical(bloqueada, op)
                      ? 'A conta mudou enquanto o app estava offline (vetor ${vetorTexto(bloqueada.vetorConhecido)} -> ${vetorTexto(vetorAgencia)}).'
                      : 'Parada: uma operacao anterior da mesma conta entrou em conflito.',
                  saldoRealCentavos: real.saldoCentavos,
                  vetorAtualAgencia: vetorAgencia,
                );
                await armazenamento.salvarPendente(marcada);
                recusadasAgora.add(marcada);
              }
              break;
            }
            final marcada = op.copiar(
              situacao: SituacaoPendente.rejeitada,
              mensagem: 'Erro ${e.status}: ${e.mensagem}',
            );
            await armazenamento.salvarPendente(marcada);
            recusadasAgora.add(marcada);
          } on ErroDeRede {
            break;
          }
        }
      }
    } finally {
      sincronizando = false;
      _relerLocal();
      notifyListeners();
    }
    if (aplicadas > 0) {
      _avisos.add(
        aplicadas == 1
            ? '1 operacao pendente sincronizada com a agencia.'
            : '$aplicadas operacoes pendentes sincronizadas com a agencia.',
      );
    }
    if (recusadasAgora.isNotEmpty) _recusas.add(recusadasAgora);
  }

  Future<void> reaplicarSobreSaldoAtual(OperacaoPendente op) async {
    final conta = armazenamento.conta(op.dono, op.idConta);
    if (conta == null) return;
    await armazenamento.salvarPendente(
      op.copiar(
        situacao: SituacaoPendente.pendente,
        vetorConhecido: conta.vetorAtual,
        saldoVistoCentavos: conta.saldoCentavos,
      ),
    );
    _relerLocal();
    notifyListeners();
    if (online) await sincronizar();
  }

  Future<void> descartar(OperacaoPendente op) async {
    await armazenamento.removerPendente(op.idOperacao);
    _relerLocal();
    notifyListeners();
  }

  @override
  void dispose() {
    parar();
    conectividade.removeListener(notifyListeners);
    _avisos.close();
    _recusas.close();
    super.dispose();
  }
}
