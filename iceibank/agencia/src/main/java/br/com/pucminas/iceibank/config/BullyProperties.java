package br.com.pucminas.iceibank.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "iceibank.bully")
public record BullyProperties(
        @DefaultValue("true") boolean habilitado,
        @DefaultValue("3s") Duration intervaloHeartbeat,
        @DefaultValue("1s") Duration timeout,
        @DefaultValue("localhost") String hostAgencias) {
}
