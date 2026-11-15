package br.com.pucminas.iceibank.dto;

import jakarta.validation.constraints.NotNull;

public record MensagemCoordenador(
        @NotNull(message = "e obrigatorio") Integer liderId,
        @NotNull(message = "e obrigatorio") int[] vetorEnvio) {
}
