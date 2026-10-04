package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.dto.MensagemCredito;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Evento;
import br.com.pucminas.iceibank.model.TipoToken;
import br.com.pucminas.iceibank.service.ContaService;
import br.com.pucminas.iceibank.service.Mensageria;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import br.com.pucminas.iceibank.service.TransferenciaService;
import tools.jackson.databind.json.JsonMapper;

class TransferenciaServiceTest {
    private static final Autenticacao ANA = new Autenticacao("ana", "Ana", TipoToken.CLIENTE);

    private Mensageria mensageria;
    private RegistroEventos registro;
    private ContaService contaService;
    private TransferenciaService transferencias;

    @BeforeEach
    void preparar(@TempDir Path pasta) throws IOException {
        var propriedades = new IceibankProperties(0, 0, pasta.toString(), null, List.of());
        AgenciaConfig config = new AgenciaConfig(propriedades);
        RelogioVetorial relogio = new RelogioVetorial(0, AgenciaConfig.NUMERO_AGENCIAS);
        registro = new RegistroEventos("agencia-0", pasta, JsonMapper.builder().build());
        mensageria = mock(Mensageria.class);
        contaService = new ContaService(config, relogio, registro);
        transferencias = new TransferenciaService(contaService, config, relogio, registro, mensageria);

        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);
    }

    @Test
    void transferenciaEntreAgenciasPublicaNaRoutingKeyDoDestinoComOVetorDeEnvio() {
        TransferenciaResponse resposta = transferencias.transferir(0, 1, BigDecimal.valueOf(30), ANA);

        ArgumentCaptor<MensagemCredito> mensagem = ArgumentCaptor.forClass(MensagemCredito.class);
        verify(mensageria).publicar(eq("agencia.1.creditar"), mensagem.capture(), eq(resposta.idTransferencia()));

        // criar conta [1,0,0] -> debito [2,0,0] -> envio [3,0,0]
        assertThat(mensagem.getValue().vetorEnvio()).containsExactly(3, 0, 0);
        assertThat(mensagem.getValue().idConta()).isEqualTo(1);
        assertThat(resposta.escopo()).isEqualTo("ENTRE_AGENCIAS");
        assertThat(resposta.saldoOrigem()).isEqualByComparingTo("70.00");
        assertThat(registro.eventos()).extracting(Evento::tipo)
                .containsExactly("CRIAR_CONTA", "TRANSFERENCIA_DEBITO", "TRANSFERENCIA_ENVIADA");
    }

    @Test
    void transferenciaLocalNaoPassaPelaMensageria() {
        contaService.criar(3, "Ana 2", BigDecimal.ZERO, ANA);

        transferencias.transferir(0, 3, BigDecimal.TEN, ANA);

        verify(mensageria, never()).publicar(anyString(), any(), anyString());
        assertThat(contaService.buscar(3).getSaldo()).isEqualByComparingTo("10.00");
    }

    @Test
    void falhaAoPublicarEstornaODebito() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("broker fora do ar")))
                .when(mensageria).publicar(anyString(), any(), anyString());

        assertThatThrownBy(() -> transferencias.transferir(0, 1, BigDecimal.valueOf(30), ANA))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(contaService.buscar(0).getSaldo()).isEqualByComparingTo("100.00");
        assertThat(registro.eventos()).extracting(Evento::tipo)
                .containsExactly("CRIAR_CONTA", "TRANSFERENCIA_DEBITO", "TRANSFERENCIA_ESTORNADA");
    }

    @Test
    void creditoRemotoAplicaOValorEJuntaOVetorRecebido() {
        boolean aplicado = transferencias.creditarRemoto(
                new MensagemCredito("t-1", 1, 0, BigDecimal.valueOf(15), new int[] {0, 4, 2}, 1));

        assertThat(aplicado).isTrue();
        assertThat(contaService.buscar(0).getSaldo()).isEqualByComparingTo("115.00");
        Evento credito = registro.eventos().getLast();
        assertThat(credito.tipo()).isEqualTo("TRANSFERENCIA_CREDITO_REMOTO");
        // vetor local [1,0,0] (criou a conta) + recebido [0,4,2] -> max = [1,4,2] -> +1 na posicao 0
        assertThat(credito.timestampVetorial()).containsExactly(2, 4, 2);
    }

    @Test
    void creditoRemotoParaContaInexistenteFicaRegistradoComoFalha() {
        boolean aplicado = transferencias.creditarRemoto(
                new MensagemCredito("t-2", 1, 9, BigDecimal.valueOf(15), new int[] {0, 4, 0}, 1));

        assertThat(aplicado).isFalse();
        assertThat(registro.eventos().getLast().tipo()).isEqualTo("CREDITO_REMOTO_FALHOU");
        assertThat(registro.eventos().getLast().detalhes()).containsEntry("motivo", "conta nao encontrada");
    }
}
