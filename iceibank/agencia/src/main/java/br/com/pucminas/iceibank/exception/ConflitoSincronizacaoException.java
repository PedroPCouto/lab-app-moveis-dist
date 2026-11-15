package br.com.pucminas.iceibank.exception;

import org.springframework.http.HttpStatus;

import br.com.pucminas.iceibank.dto.ContaComVetor;
import br.com.pucminas.iceibank.service.RelogioVetorial.Relacao;

public class ConflitoSincronizacaoException extends ApiException {
    private final int[] vetorConhecido;
    private final int[] vetorAtual;
    private final Relacao relacao;
    private final ContaComVetor contaAtual;

    public ConflitoSincronizacaoException(int[] vetorConhecido, int[] vetorAtual, Relacao relacao,
                                          ContaComVetor contaAtual) {
        super(HttpStatus.CONFLICT,
                "Conflito detectado: o estado da conta mudou desde a ultima sincronizacao do app.");
        this.vetorConhecido = vetorConhecido.clone();
        this.vetorAtual = vetorAtual.clone();
        this.relacao = relacao;
        this.contaAtual = contaAtual;
    }

    public int[] getVetorConhecido() {
        return vetorConhecido.clone();
    }

    public int[] getVetorAtual() {
        return vetorAtual.clone();
    }

    public Relacao getRelacao() {
        return relacao;
    }

    public ContaComVetor getContaAtual() {
        return contaAtual;
    }
}
