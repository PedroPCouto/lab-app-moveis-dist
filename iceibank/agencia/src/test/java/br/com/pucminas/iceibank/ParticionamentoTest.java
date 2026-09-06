package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.pucminas.iceibank.config.AgenciaConfig;

class ParticionamentoTest {
    @Test
    void contaPertenceAAgenciaDoRestoDaDivisao() {
        assertThat(AgenciaConfig.agenciaResponsavel(0)).isZero();
        assertThat(AgenciaConfig.agenciaResponsavel(1)).isEqualTo(1);
        assertThat(AgenciaConfig.agenciaResponsavel(2)).isEqualTo(2);
        assertThat(AgenciaConfig.agenciaResponsavel(3)).isZero();
        assertThat(AgenciaConfig.agenciaResponsavel(10)).isEqualTo(1);
    }

    @Test
    void portaEDerivadaDoOffsetEDaIdentidade() {
        assertThat(AgenciaConfig.portaDaAgencia(0, 0)).isEqualTo(4000);
        assertThat(AgenciaConfig.portaDaAgencia(0, 2)).isEqualTo(4002);
        assertThat(AgenciaConfig.portaDaAgencia(37, 1)).isEqualTo(4038);
    }

    @Test
    void somenteTresIdentidadesSaoValidas() {
        assertThat(AgenciaConfig.idAgenciaValido(0)).isTrue();
        assertThat(AgenciaConfig.idAgenciaValido(2)).isTrue();
        assertThat(AgenciaConfig.idAgenciaValido(3)).isFalse();
        assertThat(AgenciaConfig.idAgenciaValido(-1)).isFalse();
    }
}
