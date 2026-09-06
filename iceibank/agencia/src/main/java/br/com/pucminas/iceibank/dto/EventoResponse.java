package br.com.pucminas.iceibank.dto;

import java.util.List;

import br.com.pucminas.iceibank.model.Evento;

public record EventoResponse(
        int idConta,
        String agencia,
        int total,
        List<Evento> eventos) {
}
