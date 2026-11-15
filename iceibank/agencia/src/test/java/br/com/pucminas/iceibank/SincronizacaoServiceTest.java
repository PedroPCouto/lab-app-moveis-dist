package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.dto.SincronizacaoRequest;
import br.com.pucminas.iceibank.dto.SincronizacaoResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.exception.ConflitoSincronizacaoException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.TipoToken;
import br.com.pucminas.iceibank.service.ContaService;
import br.com.pucminas.iceibank.service.Mensageria;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import br.com.pucminas.iceibank.service.RelogioVetorial.Relacao;
import br.com.pucminas.iceibank.service.SincronizacaoService;
import br.com.pucminas.iceibank.service.TransferenciaService;
import tools.jackson.databind.json.JsonMapper;

/** Parte E: o caminho feliz e, principalmente, os casos de conflito. */
class SincronizacaoServiceTest {
    private static final Autenticacao ANA = new Autenticacao("ana", "Ana", TipoToken.CLIENTE);

    private ContaService contaService;
    private RelogioVetorial relogio;
    private SincronizacaoService sincronizacao;

    @BeforeEach
    void preparar(@TempDir Path pasta) throws IOException {
        var propriedades = new IceibankProperties(0, 0, pasta.toString(), null, List.of());
        AgenciaConfig config = new AgenciaConfig(propriedades);
        relogio = new RelogioVetorial(0, AgenciaConfig.NUMERO_AGENCIAS);
        RegistroEventos registro = new RegistroEventos("agencia-0", pasta, JsonMapper.builder().build());
        contaService = new ContaService(config, relogio, registro);
        TransferenciaService transferencias = new TransferenciaService(
                contaService, config, relogio, registro, mock(Mensageria.class));
        sincronizacao = new SincronizacaoService(contaService, transferencias);

        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);
        contaService.criar(3, "Ana poupanca", BigDecimal.valueOf(10), ANA);
    }

    private int[] vetorLidoPeloApp(int id) {
        return contaService.comVetor(contaService.buscar(id)).vetorAtual();
    }

    private static SincronizacaoRequest pendente(String id, String tipo, int valor, int[] vetor) {
        return new SincronizacaoRequest(id, tipo, BigDecimal.valueOf(valor), vetor, null, null);
    }

    @Test
    void consultaDeSaldoTrazOVetorAtual() {
        // criar conta 0 -> [1,0,0], criar conta 3 -> [2,0,0]
        assertThat(vetorLidoPeloApp(0)).containsExactly(2, 0, 0);
    }

    @Test
    void vetorIgualAplicaAOperacaoEDevolveOVetorDela() {
        int[] conhecido = vetorLidoPeloApp(0);

        SincronizacaoResponse r = sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 40, conhecido), ANA);

        assertThat(r.conta().saldo()).isEqualByComparingTo("140.00");
        assertThat(r.conta().vetorAtual()).containsExactly(3, 0, 0);
        assertThat(r.duplicada()).isFalse();
    }

    @Test
    void mudancaNaContaEnquantoOAppEstavaOfflineDa409ComOEstadoReal() {
        int[] conhecido = vetorLidoPeloApp(0);
        contaService.depositar(0, BigDecimal.valueOf(50), ANA); // "por outro caminho"

        assertThatThrownBy(() -> sincronizacao.sincronizar(0, pendente("op-1", "SAQUE", 30, conhecido), ANA))
                .isInstanceOfSatisfying(ConflitoSincronizacaoException.class, conflito -> {
                    assertThat(conflito.getStatus().value()).isEqualTo(409);
                    assertThat(conflito.getVetorConhecido()).containsExactly(2, 0, 0);
                    assertThat(conflito.getVetorAtual()).containsExactly(3, 0, 0);
                    assertThat(conflito.getRelacao()).isEqualTo(Relacao.ANTES);
                    assertThat(conflito.getContaAtual().saldo()).isEqualByComparingTo("150.00");
                });
        // nada foi aplicado as cegas
        assertThat(contaService.buscar(0).getSaldo()).isEqualByComparingTo("150.00");
    }

    @Test
    void reenviarComOVetorDevolvidoNoConflitoAplica() {
        int[] conhecido = vetorLidoPeloApp(0);
        contaService.depositar(0, BigDecimal.valueOf(50), ANA);
        int[] vetorDoConflito;
        try {
            sincronizacao.sincronizar(0, pendente("op-1", "SAQUE", 30, conhecido), ANA);
            throw new AssertionError("esperava 409");
        } catch (ConflitoSincronizacaoException conflito) {
            vetorDoConflito = conflito.getVetorAtual();
        }

        SincronizacaoResponse r = sincronizacao.sincronizar(0, pendente("op-2", "SAQUE", 30, vetorDoConflito), ANA);

        assertThat(r.conta().saldo()).isEqualByComparingTo("120.00");
    }

    @Test
    void eventoEmOutraContaDaMesmaAgenciaTambemEhConflitoSimplificacaoDoRoteiro() {
        int[] conhecido = vetorLidoPeloApp(0);
        contaService.depositar(3, BigDecimal.ONE, ANA); // outra conta, mesma agencia

        assertThatThrownBy(() -> sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 5, conhecido), ANA))
                .isInstanceOf(ConflitoSincronizacaoException.class);
    }

    @Test
    void vetorDoAppAFrenteDaAgenciaTambemEhConflito() {
        // ex.: a agencia reiniciou sem log; o app conhece um vetor que ela "esqueceu"
        assertThatThrownBy(() -> sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 5, new int[] {9, 0, 0}), ANA))
                .isInstanceOfSatisfying(ConflitoSincronizacaoException.class,
                        conflito -> assertThat(conflito.getRelacao()).isEqualTo(Relacao.DEPOIS));
    }

    @Test
    void reenvioDaMesmaOperacaoNaoAplicaDuasVezes() {
        int[] conhecido = vetorLidoPeloApp(0);
        sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 40, conhecido), ANA);

        // a resposta se perdeu e o app reenvia exatamente a mesma operacao
        SincronizacaoResponse r = sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 40, conhecido), ANA);

        assertThat(r.duplicada()).isTrue();
        assertThat(contaService.buscar(0).getSaldo()).isEqualByComparingTo("140.00");
    }

    @Test
    void duasOperacoesOfflineEmSequenciaUsamOVetorDevolvidoPelaPrimeira() {
        int[] conhecido = vetorLidoPeloApp(0);
        SincronizacaoResponse primeira = sincronizacao.sincronizar(0, pendente("op-1", "DEPOSITO", 10, conhecido), ANA);

        SincronizacaoResponse segunda = sincronizacao.sincronizar(0,
                pendente("op-2", "SAQUE", 5, primeira.conta().vetorAtual()), ANA);

        assertThat(segunda.conta().saldo()).isEqualByComparingTo("105.00");
    }

    @Test
    void saqueSemSaldoComVetorCertoDa400ENaoMexeNoVetor() {
        int[] conhecido = vetorLidoPeloApp(3);

        assertThatThrownBy(() -> sincronizacao.sincronizar(3, pendente("op-1", "SAQUE", 11, conhecido), ANA))
                .isInstanceOfSatisfying(ApiException.class,
                        erro -> assertThat(erro.getStatus().value()).isEqualTo(400));
        assertThat(relogio.valorAtual()).containsExactly(conhecido);
    }

    @Test
    void tipoDesconhecidoDa400() {
        assertThatThrownBy(() -> sincronizacao.sincronizar(0, pendente("op-1", "PIX", 1, vetorLidoPeloApp(0)), ANA))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nao suportado");
    }

    @Test
    void transferenciaOfflineLocalTambemUsaOVetor() {
        int[] conhecido = vetorLidoPeloApp(0);
        var req = new SincronizacaoRequest("op-1", "TRANSFERENCIA", BigDecimal.valueOf(25), conhecido, 3, null);

        SincronizacaoResponse r = sincronizacao.sincronizar(0, req, ANA);

        assertThat(r.conta().saldo()).isEqualByComparingTo("75.00");
        assertThat(contaService.buscar(3).getSaldo()).isEqualByComparingTo("35.00");
        assertThat(r.transferencia().escopo()).isEqualTo("LOCAL");
    }

    @Test
    void transferenciaOfflineComVetorVelhoDa409ENaoDebita() {
        int[] conhecido = vetorLidoPeloApp(0);
        contaService.sacar(0, BigDecimal.ONE, ANA);
        var req = new SincronizacaoRequest("op-1", "TRANSFERENCIA", BigDecimal.valueOf(25), conhecido, 3, null);

        assertThatThrownBy(() -> sincronizacao.sincronizar(0, req, ANA))
                .isInstanceOf(ConflitoSincronizacaoException.class);
        assertThat(contaService.buscar(0).getSaldo()).isEqualByComparingTo("99.00");
    }
}
