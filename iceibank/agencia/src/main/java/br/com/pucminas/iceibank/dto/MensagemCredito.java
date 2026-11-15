package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

public record MensagemCredito(
        String idTransferencia,
        int idOrigem,
        int idConta,
        BigDecimal valor,
        int[] vetorEnvio,
        int origemAgencia) {
}
