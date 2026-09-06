package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CriarContaRequest(
        @NotNull(message = "e obrigatorio") Integer id,
        String nomeAluno,
        @PositiveOrZero(message = "nao pode ser negativo") BigDecimal saldoInicial) {
}
