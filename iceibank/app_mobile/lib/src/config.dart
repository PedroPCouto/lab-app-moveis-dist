import 'package:flutter/foundation.dart';

/// Onde estao as 3 agencias. Mesma regra do backend e do frontend web: a agencia N
/// escuta em `4000 + OFFSET + N`, e a conta X pertence a agencia `X % 3`.
///
/// O host muda conforme onde o app roda:
/// - navegador / desktop no mesmo PC das agencias: `localhost`;
/// - emulador Android: `10.0.2.2` (o "localhost do PC" visto de dentro do emulador);
/// - celular fisico: o IP do PC na rede Wi-Fi (configuravel na tela de login).
class ConfigAgencias {
  static const int numeroAgencias = 3;
  static const int portaInicial = 4000;

  final String host;
  final int offset;

  const ConfigAgencias({required this.host, this.offset = 0});

  /// `--dart-define=ICEIBANK_HOST=...` e `--dart-define=ICEIBANK_OFFSET=..` tem prioridade.
  factory ConfigAgencias.padrao() {
    const hostDefinido = String.fromEnvironment('ICEIBANK_HOST');
    const offset = int.fromEnvironment('ICEIBANK_OFFSET', defaultValue: 0);
    if (hostDefinido.isNotEmpty) {
      return const ConfigAgencias(host: hostDefinido, offset: offset);
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return const ConfigAgencias(host: '10.0.2.2', offset: offset);
    }
    return const ConfigAgencias(host: 'localhost', offset: offset);
  }

  ConfigAgencias comHost(String novoHost) => ConfigAgencias(host: novoHost, offset: offset);

  int porta(int idAgencia) => portaInicial + offset + idAgencia;

  Uri url(int idAgencia, String caminho) => Uri.parse('http://$host:${porta(idAgencia)}$caminho');

  static int agenciaResponsavel(int idConta) =>
      ((idConta % numeroAgencias) + numeroAgencias) % numeroAgencias;

  static List<int> get todasAsAgencias => List.generate(numeroAgencias, (i) => i);
}

/// Intervalos do app. O painel da lider consulta no mesmo ritmo do heartbeat das
/// agencias (3 s): mais rapido que isso nao mostraria nada novo, porque as proprias
/// agencias so percebem a queda no heartbeat seguinte (ver RESPOSTAS.md, Parte D).
class Intervalos {
  static const pollingLider = Duration(seconds: 3);
  static const pollingAgencias = Duration(seconds: 5);
  static const recarregarContas = Duration(seconds: 10);
  static const tentarSincronizar = Duration(seconds: 4);

  static const timeoutConsulta = Duration(milliseconds: 1500);
  static const timeoutOperacao = Duration(seconds: 4);
}
