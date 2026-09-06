package br.com.pucminas.iceibank.config;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

@Component
public class AgenciaConfig {
    public static final int NUMERO_AGENCIAS = 3;

    public static final int PORTA_INICIAL = 4000;

    private final int offset;

    private final int idAgencia;

    private final List<Agencia> agencias;

    public record Agencia(int id, String url) { }

    public AgenciaConfig(IceibankProperties propriedades) {
        this.offset = propriedades.offset();
        this.idAgencia = propriedades.agenciaId();
        int offset = this.offset;
        this.agencias = IntStream.range(0, NUMERO_AGENCIAS)
                .mapToObj(id -> new Agencia(id, "http://localhost:" + portaDaAgencia(offset, id)))
                .toList();
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

    public String urlDe(int idAgencia) {
        return agencias.stream()
                .filter(a -> a.id() == idAgencia)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Agencia " + idAgencia + " nao configurada"))
                .url();
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

    public List<Agencia> getAgencias() {
        return agencias;
    }
}
