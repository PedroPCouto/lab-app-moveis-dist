package br.com.pucminas.iceibank.model;

import java.util.Map;

public record Evento(
        String agencia,
        String tipo,
        int[] timestampVetorial,
        String horaParede,
        Map<String, Object> detalhes) {
}
