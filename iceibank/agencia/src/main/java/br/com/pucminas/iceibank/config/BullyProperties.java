package br.com.pucminas.iceibank.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sprint 3: parametros da eleicao de lider (Bully) e do heartbeat. Ficam fora do codigo
 * para poderem ser trocados por variavel de ambiente (ver application.yaml) - o mesmo
 * jar serve para a demonstracao em sala (heartbeat de 3 s) e para experimentos com
 * timeouts maiores ou menores (perguntas da Parte B).
 *
 * @param habilitado         false desliga eleicao e heartbeat (usado nos testes de API)
 * @param intervaloHeartbeat de quanto em quanto tempo a agencia confere se a lider responde
 * @param timeout            quanto esperar por uma resposta antes de considerar a agencia fora do ar
 * @param hostAgencias       host onde as 3 agencias escutam (porta = 4000 + OFFSET + id)
 */
@ConfigurationProperties(prefix = "iceibank.bully")
public record BullyProperties(
        @DefaultValue("true") boolean habilitado,
        @DefaultValue("3s") Duration intervaloHeartbeat,
        @DefaultValue("1s") Duration timeout,
        @DefaultValue("localhost") String hostAgencias) {
}
