import 'config.dart';

int centavosDe(num valor) => (valor * 100).round();

String reais(int centavos) {
  final negativo = centavos < 0;
  final abs = centavos.abs();
  final inteiro = (abs ~/ 100).toString();
  final grupos = <String>[];
  for (var fim = inteiro.length; fim > 0; fim -= 3) {
    grupos.insert(0, inteiro.substring(fim - 3 < 0 ? 0 : fim - 3, fim));
  }
  return '${negativo ? '-' : ''}R\$ ${grupos.join('.')},${(abs % 100).toString().padLeft(2, '0')}';
}

int? centavosDoTexto(String texto) {
  var t = texto.trim().replaceAll('R\$', '').replaceAll(' ', '');
  if (t.isEmpty) return null;
  if (t.contains(',')) {
    t = t.replaceAll('.', '').replaceAll(',', '.');
  }
  final valor = double.tryParse(t);
  if (valor == null || valor <= 0) return null;
  return centavosDe(valor);
}

String hora(DateTime instante) {
  final l = instante.toLocal();
  String d(int n) => n.toString().padLeft(2, '0');
  return '${d(l.hour)}:${d(l.minute)}:${d(l.second)}';
}

String vetorTexto(List<int> vetor) => '[${vetor.join(',')}]';

List<int> _vetor(dynamic bruto) => (bruto as List).map((e) => (e as num).toInt()).toList();

class Conta {
  final int id;
  final String nomeAluno;
  final String dono;
  final int saldoCentavos;
  final List<int> vetorAtual;
  final DateTime atualizadaEm;

  const Conta({
    required this.id,
    required this.nomeAluno,
    required this.dono,
    required this.saldoCentavos,
    required this.vetorAtual,
    required this.atualizadaEm,
  });

  int get agencia => ConfigAgencias.agenciaResponsavel(id);

  factory Conta.daApi(Map<String, dynamic> json) => Conta(
    id: (json['id'] as num).toInt(),
    nomeAluno: (json['nomeAluno'] ?? '') as String,
    dono: (json['dono'] ?? '') as String,
    saldoCentavos: centavosDe(json['saldo'] as num),
    vetorAtual: json['vetorAtual'] == null ? const [] : _vetor(json['vetorAtual']),
    atualizadaEm: DateTime.now(),
  );

  factory Conta.doArmazenamento(Map<String, dynamic> json) => Conta(
    id: json['id'] as int,
    nomeAluno: json['nomeAluno'] as String,
    dono: json['dono'] as String,
    saldoCentavos: json['saldoCentavos'] as int,
    vetorAtual: _vetor(json['vetorAtual']),
    atualizadaEm: DateTime.parse(json['atualizadaEm'] as String),
  );

  Map<String, dynamic> paraArmazenamento() => {
    'id': id,
    'nomeAluno': nomeAluno,
    'dono': dono,
    'saldoCentavos': saldoCentavos,
    'vetorAtual': vetorAtual,
    'atualizadaEm': atualizadaEm.toIso8601String(),
  };
}

enum TipoOperacao { deposito, saque, transferencia }

extension TipoOperacaoApi on TipoOperacao {
  String get api => switch (this) {
    TipoOperacao.deposito => 'DEPOSITO',
    TipoOperacao.saque => 'SAQUE',
    TipoOperacao.transferencia => 'TRANSFERENCIA',
  };

  String get rotulo => switch (this) {
    TipoOperacao.deposito => 'Deposito',
    TipoOperacao.saque => 'Saque',
    TipoOperacao.transferencia => 'Transferencia',
  };

  int sinal() => this == TipoOperacao.deposito ? 1 : -1;
}

enum SituacaoPendente { pendente, conflito, rejeitada }

class OperacaoPendente {
  final String idOperacao;
  final String dono;
  final TipoOperacao tipo;
  final int idConta;
  final int? idDestino;
  final int valorCentavos;

  final List<int> vetorConhecido;

  final int saldoVistoCentavos;
  final DateTime criadaEm;
  final int ordem;
  final SituacaoPendente situacao;
  final String? mensagem;

  final int? saldoRealCentavos;
  final List<int>? vetorAtualAgencia;

  const OperacaoPendente({
    required this.idOperacao,
    required this.dono,
    required this.tipo,
    required this.idConta,
    this.idDestino,
    required this.valorCentavos,
    required this.vetorConhecido,
    required this.saldoVistoCentavos,
    required this.criadaEm,
    required this.ordem,
    this.situacao = SituacaoPendente.pendente,
    this.mensagem,
    this.saldoRealCentavos,
    this.vetorAtualAgencia,
  });

  bool get contaNoSaldoProvisorio => situacao == SituacaoPendente.pendente;

  int get efeitoCentavos => tipo.sinal() * valorCentavos;

  String get descricao => tipo == TipoOperacao.transferencia
      ? '${tipo.rotulo} de ${reais(valorCentavos)} da conta $idConta para a conta $idDestino'
      : '${tipo.rotulo} de ${reais(valorCentavos)} na conta $idConta';

  OperacaoPendente copiar({
    SituacaoPendente? situacao,
    String? mensagem,
    List<int>? vetorConhecido,
    int? saldoVistoCentavos,
    int? saldoRealCentavos,
    List<int>? vetorAtualAgencia,
    String? idOperacao,
  }) => OperacaoPendente(
    idOperacao: idOperacao ?? this.idOperacao,
    dono: dono,
    tipo: tipo,
    idConta: idConta,
    idDestino: idDestino,
    valorCentavos: valorCentavos,
    vetorConhecido: vetorConhecido ?? this.vetorConhecido,
    saldoVistoCentavos: saldoVistoCentavos ?? this.saldoVistoCentavos,
    criadaEm: criadaEm,
    ordem: ordem,
    situacao: situacao ?? this.situacao,
    mensagem: mensagem,
    saldoRealCentavos: saldoRealCentavos ?? this.saldoRealCentavos,
    vetorAtualAgencia: vetorAtualAgencia ?? this.vetorAtualAgencia,
  );

  Map<String, dynamic> paraApi(List<int> vetor) => {
    'idOperacao': idOperacao,
    'tipo': tipo.api,
    'valor': valorCentavos / 100,
    'vetorConhecido': vetor,
    if (idDestino != null) 'idDestino': idDestino,
    'criadaEm': criadaEm.toUtc().toIso8601String(),
  };

  Map<String, dynamic> paraArmazenamento() => {
    'idOperacao': idOperacao,
    'dono': dono,
    'tipo': tipo.name,
    'idConta': idConta,
    'idDestino': idDestino,
    'valorCentavos': valorCentavos,
    'vetorConhecido': vetorConhecido,
    'saldoVistoCentavos': saldoVistoCentavos,
    'criadaEm': criadaEm.toIso8601String(),
    'ordem': ordem,
    'situacao': situacao.name,
    'mensagem': mensagem,
    'saldoRealCentavos': saldoRealCentavos,
    'vetorAtualAgencia': vetorAtualAgencia,
  };

  factory OperacaoPendente.doArmazenamento(Map<String, dynamic> j) => OperacaoPendente(
    idOperacao: j['idOperacao'] as String,
    dono: j['dono'] as String,
    tipo: TipoOperacao.values.byName(j['tipo'] as String),
    idConta: j['idConta'] as int,
    idDestino: j['idDestino'] as int?,
    valorCentavos: j['valorCentavos'] as int,
    vetorConhecido: _vetor(j['vetorConhecido']),
    saldoVistoCentavos: j['saldoVistoCentavos'] as int,
    criadaEm: DateTime.parse(j['criadaEm'] as String),
    ordem: j['ordem'] as int,
    situacao: SituacaoPendente.values.byName(j['situacao'] as String),
    mensagem: j['mensagem'] as String?,
    saldoRealCentavos: j['saldoRealCentavos'] as int?,
    vetorAtualAgencia: j['vetorAtualAgencia'] == null ? null : _vetor(j['vetorAtualAgencia']),
  );
}

class Sessao {
  final String token;
  final String usuario;
  final String nome;
  final int agenciaEntrada;
  final String host;

  const Sessao({
    required this.token,
    required this.usuario,
    required this.nome,
    required this.agenciaEntrada,
    required this.host,
  });

  Map<String, dynamic> paraArmazenamento() => {
    'token': token,
    'usuario': usuario,
    'nome': nome,
    'agenciaEntrada': agenciaEntrada,
    'host': host,
  };

  factory Sessao.doArmazenamento(Map<String, dynamic> j) => Sessao(
    token: j['token'] as String,
    usuario: j['usuario'] as String,
    nome: j['nome'] as String,
    agenciaEntrada: j['agenciaEntrada'] as int,
    host: j['host'] as String,
  );
}

class TrocaDeLider {
  final int? de;
  final int para;
  final DateTime quando;
  final int informadaPor;

  const TrocaDeLider({required this.de, required this.para, required this.quando, required this.informadaPor});

  Map<String, dynamic> paraArmazenamento() => {
    'de': de,
    'para': para,
    'quando': quando.toIso8601String(),
    'informadaPor': informadaPor,
  };

  factory TrocaDeLider.doArmazenamento(Map<String, dynamic> j) => TrocaDeLider(
    de: j['de'] as int?,
    para: j['para'] as int,
    quando: DateTime.parse(j['quando'] as String),
    informadaPor: j['informadaPor'] as int,
  );
}
