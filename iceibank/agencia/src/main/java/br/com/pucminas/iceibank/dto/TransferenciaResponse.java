package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

public record TransferenciaResponse(
        String mensagem,
        String escopo,
        int idOrigem,
        int idDestino,
        BigDecimal valor,
        BigDecimal saldoOrigem,
        int agenciaDestino,
        int[] timestampVetorial,
        String idTransferencia) {
}
