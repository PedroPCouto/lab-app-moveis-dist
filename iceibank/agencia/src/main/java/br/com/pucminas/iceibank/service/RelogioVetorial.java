package br.com.pucminas.iceibank.service;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.pucminas.iceibank.config.AgenciaConfig;

@Component
public class RelogioVetorial {
    private final int idAgencia;
    private final int[] vetor;

    @Autowired
    public RelogioVetorial(AgenciaConfig config, RegistroEventos registro) {
        this(config.getIdAgencia(), AgenciaConfig.NUMERO_AGENCIAS);
        registro.ultimoVetorRegistrado().ifPresent(this::restaurar);
    }

    public RelogioVetorial(int idAgencia, int numeroAgencias) {
        this.idAgencia = idAgencia;
        this.vetor = new int[numeroAgencias];
    }

    public synchronized int[] eventoLocal() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoEnviar() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoReceber(int[] vetorRecebido) {
        if (vetorRecebido == null || vetorRecebido.length != vetor.length) {
            throw new IllegalArgumentException("Vetor recebido " + Arrays.toString(vetorRecebido)
                    + " nao tem " + vetor.length + " posicoes.");
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], vetorRecebido[i]);
        }
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] valorAtual() {
        return vetor.clone();
    }

    public int[] obterVetorAtual() {
        return valorAtual();
    }

    public synchronized Optional<int[]> eventoLocalSeIgual(int[] esperado) {
        if (esperado == null || !Arrays.equals(vetor, esperado)) {
            return Optional.empty();
        }
        vetor[idAgencia] += 1;
        return Optional.of(vetor.clone());
    }

    synchronized void restaurar(int[] vetorSalvo) {
        for (int i = 0; i < Math.min(vetor.length, vetorSalvo.length); i++) {
            vetor[i] = Math.max(vetor[i], vetorSalvo[i]);
        }
    }

    public static Relacao comparar(int[] v1, int[] v2) {
        boolean v1MenorOuIgual = true;
        boolean v2MenorOuIgual = true;
        for (int i = 0; i < v1.length; i++) {
            if (v1[i] > v2[i]) {
                v1MenorOuIgual = false;
            }
            if (v2[i] > v1[i]) {
                v2MenorOuIgual = false;
            }
        }
        if (v1MenorOuIgual && v2MenorOuIgual) {
            return Relacao.IGUAIS;
        }
        if (v1MenorOuIgual) {
            return Relacao.ANTES;
        }
        if (v2MenorOuIgual) {
            return Relacao.DEPOIS;
        }
        return Relacao.CONCORRENTES;
    }

    public enum Relacao {
        ANTES,
        DEPOIS,
        IGUAIS,
        CONCORRENTES
    }
}
