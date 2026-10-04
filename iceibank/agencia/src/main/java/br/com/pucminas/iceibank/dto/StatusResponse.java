package br.com.pucminas.iceibank.dto;

import java.util.List;

public record StatusResponse(
        String situacao,
        int idAgencia,
        int porta,
        int numeroAgencias,
        int[] relogioVetorial,
        int quantidadeContas,
        List<Integer> contas,
        int eventosRegistrados,
        String horaParede) {
}
