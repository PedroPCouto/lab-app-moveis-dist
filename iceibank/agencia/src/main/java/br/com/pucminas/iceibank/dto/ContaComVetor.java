package br.com.pucminas.iceibank.dto;

import java.math.BigDecimal;

import br.com.pucminas.iceibank.model.Conta;

/**
 * Sprint 3, Parte E: a conta junto com o vetor da agencia NO MESMO INSTANTE em que o saldo
 * foi lido (snapshot tirado com a conta travada). E esse par (saldo, vetor) que o app
 * guarda para trabalhar offline e devolve como {@code vetorConhecido} ao sincronizar.
 */
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
