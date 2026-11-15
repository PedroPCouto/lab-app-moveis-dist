package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de {@code POST /contas/{id}/sincronizar}: uma operacao que o app registrou enquanto
 * estava offline, junto com o vetor que ele conhecia quando a registrou.
 *
 * @param idOperacao     identificador gerado pelo app (UUID); torna o reenvio idempotente
 * @param tipo           DEPOSITO, SAQUE ou TRANSFERENCIA
 * @param vetorConhecido o {@code vetorAtual} da ultima leitura da conta feita pelo app
 * @param idDestino      so para TRANSFERENCIA
 * @param criadaEm       hora (do celular) em que a operacao foi feita offline - so informativo
 */
public record SincronizacaoRequest(
        String idOperacao,
        @NotBlank(message = "e obrigatorio") String tipo,
        @NotNull(message = "e obrigatorio") @Positive(message = "deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "e obrigatorio") int[] vetorConhecido,
        Integer idDestino,
        String criadaEm) {
}
