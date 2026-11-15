package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SincronizacaoRequest(
        String idOperacao,
        @NotBlank(message = "e obrigatorio") String tipo,
        @NotNull(message = "e obrigatorio") @Positive(message = "deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "e obrigatorio") int[] vetorConhecido,
        Integer idDestino,
        String criadaEm) {
}
