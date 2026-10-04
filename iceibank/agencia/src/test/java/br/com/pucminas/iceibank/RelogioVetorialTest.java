package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import br.com.pucminas.iceibank.service.RelogioVetorial.Relacao;
import tools.jackson.databind.json.JsonMapper;

class RelogioVetorialTest {
    @Test
    void eventoLocalIncrementaSoAPropriaPosicao() {
        RelogioVetorial relogio = new RelogioVetorial(1, 3);

        assertThat(relogio.eventoLocal()).containsExactly(0, 1, 0);
        assertThat(relogio.eventoLocal()).containsExactly(0, 2, 0);
    }

    @Test
    void aoEnviarIncrementaAntesDeAnexarOVetor() {
        RelogioVetorial relogio = new RelogioVetorial(0, 3);
        relogio.eventoLocal();

        assertThat(relogio.aoEnviar()).containsExactly(2, 0, 0);
    }

    @Test
    void aoReceberFazOMaximoPosicaoAPosicaoEIncrementaAPropria() {
        RelogioVetorial relogio = new RelogioVetorial(1, 3);
        relogio.eventoLocal();
        relogio.eventoLocal();

        assertThat(relogio.aoReceber(new int[] {4, 1, 7})).containsExactly(4, 3, 7);
    }

    @Test
    void vetorDevolvidoEUmaCopia() {
        RelogioVetorial relogio = new RelogioVetorial(0, 3);
        int[] copia = relogio.eventoLocal();
        copia[0] = 99;

        assertThat(relogio.valorAtual()).containsExactly(1, 0, 0);
    }

    @Test
    void recusaVetorDeTamanhoDiferente() {
        RelogioVetorial relogio = new RelogioVetorial(0, 3);

        assertThatThrownBy(() -> relogio.aoReceber(new int[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesmaSequenciaDoRoteiroEmNodeJavaEPython() {
        RelogioVetorial a0 = new RelogioVetorial(0, 3);
        RelogioVetorial a1 = new RelogioVetorial(1, 3);

        int[] debito = a0.eventoLocal();
        int[] envio = a0.aoEnviar();
        int[] credito = a1.aoReceber(envio);

        assertThat(debito).containsExactly(1, 0, 0);
        assertThat(envio).containsExactly(2, 0, 0);
        assertThat(credito).containsExactly(2, 1, 0);
    }

    @Test
    void comparaRelacoesDasPerguntasDaParteB() {
        assertThat(RelogioVetorial.comparar(new int[] {3, 1, 0}, new int[] {3, 2, 0})).isEqualTo(Relacao.ANTES);
        assertThat(RelogioVetorial.comparar(new int[] {3, 2, 0}, new int[] {3, 1, 0})).isEqualTo(Relacao.DEPOIS);
        assertThat(RelogioVetorial.comparar(new int[] {3, 1, 0}, new int[] {1, 3, 0}))
                .isEqualTo(Relacao.CONCORRENTES);
        assertThat(RelogioVetorial.comparar(new int[] {1, 1, 1}, new int[] {1, 1, 1})).isEqualTo(Relacao.IGUAIS);
    }

    @Test
    void retomaOVetorGravadoNoLogDepoisDeUmReinicio(@TempDir Path pasta) throws IOException {
        var propriedades = new IceibankProperties(0, 1, pasta.toString(), null, List.of());
        AgenciaConfig config = new AgenciaConfig(propriedades);
        RegistroEventos registro = new RegistroEventos("agencia-1", pasta, JsonMapper.builder().build());
        registro.registrar("CRIAR_CONTA", new int[] {2, 5, 0}, Map.of("id", 1));

        RelogioVetorial depoisDoReinicio = new RelogioVetorial(config, registro);

        assertThat(depoisDoReinicio.eventoLocal()).containsExactly(2, 6, 0);
    }

    @Test
    void contadorNaoPerdeIncrementosSobConcorrencia() throws InterruptedException {
        RelogioVetorial relogio = new RelogioVetorial(2, 3);
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

        assertThat(relogio.valorAtual()).containsExactly(0, 0, threads * eventosPorThread);
    }
}
