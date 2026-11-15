package br.com.pucminas.iceibank.dto;

import jakarta.validation.constraints.NotNull;

public record MensagemEleicao(
        @NotNull(message = "e obrigatorio") Integer origemId,
        @NotNull(message = "e obrigatorio") int[] vetorEnvio) {
}
