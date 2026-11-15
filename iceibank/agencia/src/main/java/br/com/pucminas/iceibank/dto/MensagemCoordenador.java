package br.com.pucminas.iceibank.dto;

import jakarta.validation.constraints.NotNull;

/** Corpo de {@code POST /bully/coordenador}: "a agencia {@code liderId} venceu a eleicao". */
public record MensagemCoordenador(
        @NotNull(message = "e obrigatorio") Integer liderId,
        @NotNull(message = "e obrigatorio") int[] vetorEnvio) {
}
