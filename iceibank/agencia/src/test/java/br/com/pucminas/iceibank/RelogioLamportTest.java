package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.pucminas.iceibank.service.RelogioLamport;

class RelogioLamportTest {
    @Test
    void eventoLocalIncrementaDeUmEmUm() {
        RelogioLamport relogio = new RelogioLamport();

        assertThat(relogio.eventoLocal()).isEqualTo(1);
        assertThat(relogio.eventoLocal()).isEqualTo(2);
        assertThat(relogio.valorAtual()).isEqualTo(2);
    }

    @Test
    void aoEnviarIncrementaAntesDeAnexarOTimestamp() {
        RelogioLamport relogio = new RelogioLamport();
        relogio.eventoLocal();

        assertThat(relogio.aoEnviar()).isEqualTo(2);
    }

    @Test
    void aoReceberAvancaQuandoOTimestampRecebidoEMaior() {
        RelogioLamport relogio = new RelogioLamport();
        relogio.eventoLocal();

        assertThat(relogio.aoReceber(7)).isEqualTo(8);
    }

    @Test
    void aoReceberNuncaRetrocedeOContador() {
        RelogioLamport relogio = new RelogioLamport();
        for (int i = 0; i < 10; i++) {
            relogio.eventoLocal();
        }

        assertThat(relogio.aoReceber(3)).isEqualTo(11);
    }

    @Test
    void contadorNaoPerdeIncrementosSobConcorrencia() throws InterruptedException {
        RelogioLamport relogio = new RelogioLamport();
        int threads = 8;
        int eventosPorThread = 1000;

        Thread[] trabalhadores = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            trabalhadores[i] = new Thread(() -> {
                for (int j = 0; j < eventosPorThread; j++) {
                    relogio.eventoLocal();
                }
            });
            trabalhadores[i].start();
        }
        for (Thread trabalhador : trabalhadores) {
            trabalhador.join();
        }

        assertThat(relogio.valorAtual()).isEqualTo(threads * eventosPorThread);
    }
}
