package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.TipoToken;
import br.com.pucminas.iceibank.service.ContaService;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import tools.jackson.databind.json.JsonMapper;

class ContaServiceTest {
    private static final Autenticacao ANA = new Autenticacao("ana", "Ana", TipoToken.CLIENTE);
    private static final Autenticacao BRUNO = new Autenticacao("bruno", "Bruno", TipoToken.CLIENTE);

    private ContaService contaService;

    @BeforeEach
    void preparar(@TempDir Path pastaTemporaria) throws IOException {
        var propriedades = new IceibankProperties(0, 0, pastaTemporaria.toString(),
                new IceibankProperties.Jwt(IceibankProperties.SEGREDO_PADRAO, null), List.of());
        AgenciaConfig config = new AgenciaConfig(propriedades);
        RegistroEventos registro = new RegistroEventos("agencia-0", pastaTemporaria, JsonMapper.builder().build());
        contaService = new ContaService(config, new RelogioVetorial(0, AgenciaConfig.NUMERO_AGENCIAS), registro);
    }

    @Test
    void recusaContaQueNaoPertenceAEstaAgencia() {
        assertThatThrownBy(() -> contaService.criar(1, "Bruno", BigDecimal.TEN, BRUNO))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nao pertence a esta agencia");
    }

    @Test
    void recusaContaDuplicada() {
        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);

        assertThatThrownBy(() -> contaService.criar(0, "Ana de novo", BigDecimal.ZERO, ANA))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ja existe");
    }

    @Test
    void depositoESaqueAtualizamOSaldo() {
        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);

        assertThat(contaService.depositar(0, BigDecimal.valueOf(25), ANA).getSaldo())
                .isEqualByComparingTo("125.00");
        assertThat(contaService.sacar(0, BigDecimal.valueOf(30), ANA).getSaldo())
                .isEqualByComparingTo("95.00");
    }

    @Test
    void recusaSaqueAcimaDoSaldo() {
        contaService.criar(0, "Ana", BigDecimal.valueOf(10), ANA);

        assertThatThrownBy(() -> contaService.sacar(0, BigDecimal.valueOf(11), ANA))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void usuarioAutenticadoNaoOperaContaDeOutro() {
        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);

        assertThatThrownBy(() -> contaService.sacar(0, BigDecimal.ONE, BRUNO))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nao pertence ao usuario autenticado");
    }

    @Test
    void historicoTrazSomenteOsEventosDaConta() {
        contaService.criar(0, "Ana", BigDecimal.valueOf(100), ANA);
        contaService.criar(3, "Ana 2", BigDecimal.valueOf(10), ANA);
        contaService.depositar(0, BigDecimal.valueOf(5), ANA);

        assertThat(contaService.historico(0, 10))
                .extracting("tipo")
                .containsExactly("DEPOSITO", "CRIAR_CONTA");
    }
}
