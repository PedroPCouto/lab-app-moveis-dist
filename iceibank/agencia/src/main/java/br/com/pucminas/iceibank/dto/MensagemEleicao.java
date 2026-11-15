package br.com.pucminas.iceibank.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo de {@code POST /bully/eleicao}: "sou a agencia {@code origemId} e estou fazendo
 * uma eleicao". O vetor viaja junto, como toda mensagem entre agencias desde o Sprint 2.
 */
public record MensagemEleicao(
        @NotNull(message = "e obrigatorio") Integer origemId,
        @NotNull(message = "e obrigatorio") int[] vetorEnvio) {
}
