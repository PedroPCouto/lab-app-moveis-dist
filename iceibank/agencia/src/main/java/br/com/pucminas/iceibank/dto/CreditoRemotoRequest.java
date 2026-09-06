package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreditoRemotoRequest(
        @NotNull(message = "e obrigatorio") @Positive(message = "deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "e obrigatorio") Integer timestampLamport,
        @NotNull(message = "e obrigatorio") Integer origemAgencia) {
}
