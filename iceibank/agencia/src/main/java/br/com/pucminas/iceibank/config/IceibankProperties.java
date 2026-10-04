package br.com.pucminas.iceibank.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "iceibank")
public record IceibankProperties(
        @DefaultValue("0") int offset,
        @DefaultValue("0") int agenciaId,
        @DefaultValue("data") String pastaDados,
        @DefaultValue Jwt jwt,
        @DefaultValue List<UsuarioProps> usuarios) {
    public static final String SEGREDO_PADRAO = "iceibank-sprint1-chave-de-laboratorio-nao-use-em-producao";

    public record Jwt(
            @DefaultValue(SEGREDO_PADRAO) String segredo,
            @DefaultValue("30m") Duration expiracao) {
    }

    public record UsuarioProps(String usuario, String nome, String senhaHash) {
    }
}
