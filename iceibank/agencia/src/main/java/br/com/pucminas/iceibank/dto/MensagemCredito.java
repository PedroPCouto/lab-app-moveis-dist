package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

/**
 * Corpo da mensagem publicada em {@code agencia.<id>.creditar}. O vetor do envio viaja
 * junto (regra 2 do relogio vetorial) e o idTransferencia liga o debito na origem ao
 * credito no destino nos logs das duas agencias.
 */
public record MensagemCredito(
        String idTransferencia,
        int idOrigem,
        int idConta,
        BigDecimal valor,
        int[] vetorEnvio,
        int origemAgencia) {
}
