package br.com.pucminas.iceibank;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import br.com.pucminas.iceibank.config.AgenciaConfig;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IceibankApplication {
	public static void main(String[] args) {
		int offset = inteiroDoAmbiente("OFFSET", 0);
		int idAgencia = inteiroDoAmbiente("AGENCIA_ID", 0);

		if (!AgenciaConfig.idAgenciaValido(idAgencia)) {
			System.err.printf("Agencia %d invalida: use AGENCIA_ID entre 0 e %d.%n",
					idAgencia, AgenciaConfig.NUMERO_AGENCIAS - 1);
			System.exit(1);
		}

		SpringApplication aplicacao = new SpringApplication(IceibankApplication.class);
		aplicacao.setDefaultProperties(Map.of(
				"server.port", String.valueOf(AgenciaConfig.portaDaAgencia(offset, idAgencia))));
		aplicacao.run(args);
	}

	private static int inteiroDoAmbiente(String variavel, int padrao) {
		String valor = System.getenv(variavel);
		if (valor == null || valor.isBlank()) {
			return padrao;
		}
		try {
			return Integer.parseInt(valor.trim());
		} catch (NumberFormatException e) {
			System.err.printf("Variavel de ambiente %s='%s' nao e um numero inteiro.%n", variavel, valor);
			System.exit(1);
			return padrao;
		}
	}
}
