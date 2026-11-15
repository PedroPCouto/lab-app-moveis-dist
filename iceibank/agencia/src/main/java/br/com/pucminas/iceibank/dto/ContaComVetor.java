package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import br.com.pucminas.iceibank.model.Conta;

public record ContaComVetor(
        int id,
        String nomeAluno,
        String dono,
        BigDecimal saldo,
        int[] vetorAtual) {
    public static ContaComVetor de(Conta conta, int[] vetor) {
        return new ContaComVetor(conta.getId(), conta.getNomeAluno(), conta.getDono(), conta.getSaldo(), vetor);
    }
}
