package br.com.pucminas.iceibank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record LiderResponse(
        int idAgencia,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer liderAtual,
        boolean eleicaoEmAndamento,
        String horaParede) {
}
