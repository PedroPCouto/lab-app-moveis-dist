package br.com.pucminas.iceibank.service;

import java.util.Arrays;

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
        // Os eventos ja gravados em disco continuam valendo depois de um reinicio. Se o
        // relogio voltasse a [0,0,0], a agencia reaproveitaria vetores que ja tinham sido
        // usados (e que outras agencias ja viram), e a analise de causalidade mentiria.
        registro.ultimoVetorRegistrado().ifPresent(this::restaurar);
    }

    public RelogioVetorial(int idAgencia, int numeroAgencias) {
        this.idAgencia = idAgencia;
        this.vetor = new int[numeroAgencias];
    }

    // Regra 1: evento local incrementa so a propria posicao.
    public synchronized int[] eventoLocal() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    // Regra 2: envio incrementa a propria posicao e o vetor inteiro vai junto da mensagem.
    public synchronized int[] aoEnviar() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    // Regra 3: recebimento faz o maximo posicao a posicao e depois incrementa a propria.
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
