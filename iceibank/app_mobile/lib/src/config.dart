import 'package:flutter/foundation.dart';

class ConfigAgencias {
  static const int numeroAgencias = 3;
  static const int portaInicial = 4000;

  final String host;
  final int offset;

  const ConfigAgencias({required this.host, this.offset = 0});

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

  static int agenciaResponsavel(int idConta) => ((idConta % numeroAgencias) + numeroAgencias) % numeroAgencias;

  static List<int> get todasAsAgencias => List.generate(numeroAgencias, (i) => i);
}

class Intervalos {
  static const pollingLider = Duration(seconds: 3);
  static const pollingAgencias = Duration(seconds: 5);
  static const recarregarContas = Duration(seconds: 10);
  static const tentarSincronizar = Duration(seconds: 4);

  static const timeoutConsulta = Duration(milliseconds: 1500);
  static const timeoutOperacao = Duration(seconds: 4);
}
