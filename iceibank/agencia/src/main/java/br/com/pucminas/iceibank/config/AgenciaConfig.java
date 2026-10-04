package br.com.pucminas.iceibank.config;

import org.springframework.stereotype.Component;

@Component
public class AgenciaConfig {
    public static final int NUMERO_AGENCIAS = 3;

    public static final int PORTA_INICIAL = 4000;

    private final int offset;

    private final int idAgencia;

    public AgenciaConfig(IceibankProperties propriedades) {
        this.offset = propriedades.offset();
        this.idAgencia = propriedades.agenciaId();
    }

    public static int agenciaResponsavel(int idConta) {
        return Math.floorMod(idConta, NUMERO_AGENCIAS);
    }

    public static int portaDaAgencia(int offset, int idAgencia) {
        return PORTA_INICIAL + offset + idAgencia;
    }

    public static boolean idAgenciaValido(int idAgencia) {
        return idAgencia >= 0 && idAgencia < NUMERO_AGENCIAS;
    }

    public int getOffset() {
        return offset;
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    public int getPorta() {
        return portaDaAgencia(offset, idAgencia);
    }

    public String getNomeAgencia() {
        return "agencia-" + idAgencia;
    }
}
