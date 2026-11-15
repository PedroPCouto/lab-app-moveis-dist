package br.com.pucminas.iceibank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de {@code GET /bully/lider}. {@code liderAtual} sai como {@code null} (e nao
 * some do JSON, como os demais campos nulos da API) quando a agencia ainda nao conhece
 * uma lider - por exemplo, no meio de uma eleicao.
 */
public record LiderResponse(
        int idAgencia,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer liderAtual,
        boolean eleicaoEmAndamento,
        String horaParede) {
}
