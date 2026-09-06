package br.com.pucminas.iceibank.service;

import org.springframework.stereotype.Component;

@Component
public class RelogioLamport {
    private int contador = 0;

    public synchronized int eventoLocal() {
        contador += 1;
        return contador;
    }

    public synchronized int aoEnviar() {
        contador += 1;
        return contador;
    }

    public synchronized int aoReceber(int timestampRecebido) {
        contador = Math.max(contador, timestampRecebido) + 1;
        return contador;
    }

    public synchronized int valorAtual() {
        return contador;
    }
}
