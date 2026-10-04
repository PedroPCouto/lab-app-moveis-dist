package br.com.pucminas.iceibank.dto;

import java.util.List;
import java.util.Map;

public record StatusResponse(
        String situacao,
        int idAgencia,
        int porta,
        int numeroAgencias,
        int[] relogioVetorial,
        int quantidadeContas,
        List<Integer> contas,
        int eventosRegistrados,
        Map<String, Long> filas,
        String horaParede) {
}
