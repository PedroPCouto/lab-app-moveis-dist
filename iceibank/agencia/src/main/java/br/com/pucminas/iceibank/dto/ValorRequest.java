package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ValorRequest(
        @NotNull(message = "e obrigatorio") @Positive(message = "deve ser maior que zero") BigDecimal valor) {
}
