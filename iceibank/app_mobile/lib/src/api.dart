import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'config.dart';

/// A agencia respondeu, mas com erro (4xx/5xx). `corpo` traz o JSON de erro inteiro -
/// no 409 da sincronizacao, os vetores e a conta atual.
class ErroApi implements Exception {
  final int status;
  final String mensagem;
  final Map<String, dynamic>? corpo;

  const ErroApi(this.status, this.mensagem, [this.corpo]);

  @override
  String toString() => mensagem;
}

/// A agencia nao respondeu (sem rede, agencia fora do ar, timeout). Para o app, e o
/// sinal de "guardar a operacao para depois" (Parte F).
class ErroDeRede implements Exception {
  final int idAgencia;
  final String detalhe;

  const ErroDeRede(this.idAgencia, this.detalhe);

  @override
  String toString() => 'Agencia $idAgencia nao respondeu ($detalhe).';
}

/// Cliente da mesma API REST que o frontend web do Sprint 1 usa. Como no frontend, o
/// token vai em toda requisicao e as 3 agencias o aceitam (mesma chave de assinatura).
class ApiCliente {
  ConfigAgencias config;
  String? token;
  final http.Client _http;

  ApiCliente(this.config, {http.Client? cliente}) : _http = cliente ?? http.Client();

  Future<dynamic> requisitar(
    String metodo,
    int agencia,
    String caminho, {
    Object? corpo,
    Duration timeout = Intervalos.timeoutOperacao,
  }) async {
    final requisicao = http.Request(metodo, config.url(agencia, caminho));
    if (token != null) requisicao.headers['Authorization'] = 'Bearer $token';
    if (corpo != null) {
      requisicao.headers['Content-Type'] = 'application/json';
      requisicao.body = jsonEncode(corpo);
    }

    http.Response resposta;
    try {
      resposta = await http.Response.fromStream(await _http.send(requisicao).timeout(timeout));
    } on TimeoutException {
      throw ErroDeRede(agencia, 'sem resposta em ${timeout.inMilliseconds} ms');
    } catch (erro) {
      throw ErroDeRede(agencia, 'conexao recusada ou sem rede');
    }

    final texto = utf8.decode(resposta.bodyBytes);
    dynamic dados;
    if (texto.isNotEmpty) {
      try {
        dados = jsonDecode(texto);
      } catch (_) {
        dados = {'erro': texto};
      }
    }
    if (resposta.statusCode >= 400) {
      final mapa = dados is Map<String, dynamic> ? dados : null;
      throw ErroApi(resposta.statusCode, (mapa?['erro'] ?? 'Erro HTTP ${resposta.statusCode}').toString(), mapa);
    }
    return dados;
  }

  Future<Map<String, dynamic>> entrar(String usuario, String senha, int agencia) async =>
      await requisitar('POST', agencia, '/auth/login', corpo: {'usuario': usuario, 'senha': senha})
          as Map<String, dynamic>;

  Future<List<dynamic>> minhasContas(int agencia) async =>
      await requisitar('GET', agencia, '/contas', timeout: Intervalos.timeoutConsulta) as List<dynamic>;

  Future<Map<String, dynamic>> conta(int id) async =>
      await requisitar('GET', ConfigAgencias.agenciaResponsavel(id), '/contas/$id') as Map<String, dynamic>;

  Future<Map<String, dynamic>> criarConta(int id, String nome, double saldoInicial) async => await requisitar(
    'POST',
    ConfigAgencias.agenciaResponsavel(id),
    '/contas',
    corpo: {'id': id, 'nomeAluno': nome, 'saldoInicial': saldoInicial},
  ) as Map<String, dynamic>;

  Future<Map<String, dynamic>> depositar(int id, int centavos) async => await requisitar(
    'POST',
    ConfigAgencias.agenciaResponsavel(id),
    '/contas/$id/depositar',
    corpo: {'valor': centavos / 100},
  ) as Map<String, dynamic>;

  Future<Map<String, dynamic>> sacar(int id, int centavos) async => await requisitar(
    'POST',
    ConfigAgencias.agenciaResponsavel(id),
    '/contas/$id/sacar',
    corpo: {'valor': centavos / 100},
  ) as Map<String, dynamic>;

  /// A transferencia vai para a agencia da conta de ORIGEM (e ela quem debita).
  Future<Map<String, dynamic>> transferir(int origem, int destino, int centavos) async => await requisitar(
    'POST',
    ConfigAgencias.agenciaResponsavel(origem),
    '/transferencias',
    corpo: {'idOrigem': origem, 'idDestino': destino, 'valor': centavos / 100},
  ) as Map<String, dynamic>;

  /// Parte E: `POST /contas/{id}/sincronizar`.
  Future<Map<String, dynamic>> sincronizar(int idConta, Map<String, dynamic> corpo) async =>
      await requisitar('POST', ConfigAgencias.agenciaResponsavel(idConta), '/contas/$idConta/sincronizar', corpo: corpo)
          as Map<String, dynamic>;

  /// Parte D: `GET /bully/lider` (publica, sem token).
  Future<Map<String, dynamic>> lider(int agencia) async =>
      await requisitar('GET', agencia, '/bully/lider', timeout: Intervalos.timeoutConsulta) as Map<String, dynamic>;

  Future<Map<String, dynamic>> status(int agencia) async =>
      await requisitar('GET', agencia, '/status', timeout: Intervalos.timeoutConsulta) as Map<String, dynamic>;
}
