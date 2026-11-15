package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.BullyProperties;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;
import br.com.pucminas.iceibank.model.Evento;
import br.com.pucminas.iceibank.service.BullyService;
import br.com.pucminas.iceibank.service.ClienteBully;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import tools.jackson.databind.json.JsonMapper;

/**
 * A logica de decisao do Bully, sem rede: as 3 agencias conversam por uma "rede" em
 * memoria onde uma agencia pode ser derrubada (passa a nao responder, como um processo
 * encerrado) e religada.
 */
class BullyServiceTest {
    @TempDir
    Path pasta;

    private final RedeEmMemoria rede = new RedeEmMemoria();
    private final Map<Integer, BullyService> agencias = new HashMap<>();
    private final Map<Integer, RegistroEventos> registros = new HashMap<>();

    @AfterEach
    void encerrar() throws InterruptedException {
        for (BullyService agencia : agencias.values()) {
            agencia.encerrar();
        }
    }

    private BullyService subir(int id) throws IOException {
        var propriedades = new IceibankProperties(0, id, pasta.toString(),
                new IceibankProperties.Jwt(IceibankProperties.SEGREDO_PADRAO, null), List.of());
        var bully = new BullyProperties(true, Duration.ofSeconds(3), Duration.ofMillis(200), "localhost");
        RegistroEventos registro = new RegistroEventos("agencia-" + id, pasta, JsonMapper.builder().build());
        BullyService servico = new BullyService(new AgenciaConfig(propriedades), bully,
                new RelogioVetorial(id, AgenciaConfig.NUMERO_AGENCIAS), registro, rede);
        agencias.put(id, servico);
        registros.put(id, registro);
        rede.ligar(id, servico);
        return servico;
    }

    private Integer lider(int id) {
        return agencias.get(id).obterLiderAtual().liderAtual();
    }

    private static void esperar(BooleanSupplier condicao, String descricao) throws InterruptedException {
        long fim = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < fim) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Tempo esgotado esperando: " + descricao);
    }

    private long contar(int id, String tipo) {
        return registros.get(id).eventos().stream().map(Evento::tipo).filter(tipo::equals).count();
    }

    @Test
    void tresAgenciasSubindoJuntasConvergemParaAMaiorId() throws Exception {
        subir(0);
        subir(1);
        subir(2);

        // a "tempestade" da subida: as tres comecam uma eleicao ao mesmo tempo
        agencias.values().forEach(BullyService::aoSubir);

        esperar(() -> Integer.valueOf(2).equals(lider(0)) && Integer.valueOf(2).equals(lider(1))
                && Integer.valueOf(2).equals(lider(2)), "as 3 concordarem que a lider e a 2");
    }

    @Test
    void semAAgencia2ALiderEaAgencia1() throws Exception {
        subir(0);
        subir(1);

        agencias.get(0).iniciarEleicao();

        esperar(() -> Integer.valueOf(1).equals(lider(0)) && Integer.valueOf(1).equals(lider(1)),
                "a 0 e a 1 concordarem que a lider e a 1");
        assertThat(contar(1, "TORNOU_LIDER")).isEqualTo(1);
        assertThat(contar(0, "TORNOU_LIDER")).isZero();
    }

    @Test
    void agenciaSozinhaViraLider() throws Exception {
        subir(0);

        agencias.get(0).iniciarEleicao();

        assertThat(lider(0)).isEqualTo(0);
    }

    @Test
    void agenciaDeIdMaiorQueVoltaRetomaALideranca() throws Exception {
        subir(0);
        subir(1);
        agencias.get(0).iniciarEleicao();
        esperar(() -> Integer.valueOf(1).equals(lider(0)), "a lider inicial ser a 1");

        subir(2).aoSubir();

        esperar(() -> Integer.valueOf(2).equals(lider(0)) && Integer.valueOf(2).equals(lider(1))
                && Integer.valueOf(2).equals(lider(2)), "a 2 retomar a lideranca ao voltar");
    }

    @Test
    void anuncioDeQuemTemIdMenorEhRecusadoEProvocaNovaEleicao() throws Exception {
        subir(0);
        subir(1);
        subir(2);

        // a 1 nao viu a 2 a tempo (ex.: a 2 ainda subia) e se anunciou para todo mundo
        agencias.get(0).receberCoordenador(1, new int[] {0, 5, 0});
        agencias.get(2).receberCoordenador(1, new int[] {0, 5, 0});

        esperar(() -> Integer.valueOf(2).equals(lider(0)) && Integer.valueOf(2).equals(lider(1))
                && Integer.valueOf(2).equals(lider(2)), "a 2 recusar o anuncio da 1 e vencer");
    }

    @Test
    void mensagensDoBullyPassamPeloRelogioVetorial() throws Exception {
        subir(0);
        subir(1);

        agencias.get(0).iniciarEleicao();
        esperar(() -> Integer.valueOf(1).equals(lider(0)), "a lider ser a 1");

        // ELEICAO_RECEBIDA na agencia 1 tem que ver o envio da 0: posicao 0 maior que zero
        Evento recebida = registros.get(1).eventos().stream()
                .filter(e -> e.tipo().equals("ELEICAO_RECEBIDA")).findFirst().orElseThrow();
        assertThat(recebida.timestampVetorial()[0]).isPositive();
        assertThat(recebida.timestampVetorial()[1]).isPositive();
    }

    @Test
    void heartbeatDetectaAQuedaDaLiderEReelegeSemIntervencao() throws Exception {
        subir(0);
        subir(1);
        subir(2);
        agencias.get(0).iniciarEleicao();
        esperar(() -> Integer.valueOf(2).equals(lider(0)) && Integer.valueOf(2).equals(lider(1)),
                "a lider inicial ser a 2");

        rede.derrubar(2);
        agencias.get(0).verificarLider();
        agencias.get(1).verificarLider();

        esperar(() -> Integer.valueOf(1).equals(lider(0)) && Integer.valueOf(1).equals(lider(1)),
                "a 0 e a 1 elegerem a 1 depois da queda da 2");
        assertThat(contar(0, "LIDER_NAO_RESPONDEU") + contar(1, "LIDER_NAO_RESPONDEU")).isPositive();
    }

    @Test
    void heartbeatComALiderRespondendoNaoMexeEmNada() throws Exception {
        subir(0);
        subir(1);
        agencias.get(0).iniciarEleicao();
        esperar(() -> Integer.valueOf(1).equals(lider(0)), "a lider ser a 1");
        long eleicoesAntes = contar(0, "ELEICAO_INICIADA");

        agencias.get(0).verificarLider();

        assertThat(lider(0)).isEqualTo(1);
        assertThat(contar(0, "ELEICAO_INICIADA")).isEqualTo(eleicoesAntes);
        assertThat(contar(0, "LIDER_NAO_RESPONDEU")).isZero();
    }

    @Test
    void heartbeatSemLiderConhecidaFazUmaEleicao() throws Exception {
        subir(0);
        subir(1);

        agencias.get(0).verificarLider();

        esperar(() -> Integer.valueOf(1).equals(lider(0)), "a 0 descobrir a lider pelo heartbeat");
    }

    @Test
    void heartbeatEmQuemEALiderNaoConsultaNinguem() throws Exception {
        subir(0);
        agencias.get(0).iniciarEleicao();
        rede.derrubar(0); // nem ela mesma responderia - e nao deve precisar

        agencias.get(0).verificarLider();

        assertThat(lider(0)).isEqualTo(0);
    }

    /** Rede de mentira: entrega a chamada direto ao servico da agencia de destino. */
    static class RedeEmMemoria implements ClienteBully {
        private final Map<Integer, BullyService> ligadas = new ConcurrentHashMap<>();
        private final Set<Integer> derrubadas = ConcurrentHashMap.newKeySet();

        void ligar(int id, BullyService servico) {
            ligadas.put(id, servico);
            derrubadas.remove(id);
        }

        void derrubar(int id) {
            derrubadas.add(id);
        }

        private Optional<BullyService> destino(String url) {
            int id = Integer.parseInt(url.substring(url.lastIndexOf(':') + 1)) - AgenciaConfig.PORTA_INICIAL;
            if (derrubadas.contains(id)) {
                return Optional.empty();
            }
            return Optional.ofNullable(ligadas.get(id));
        }

        @Override
        public boolean enviarEleicao(String urlAgencia, MensagemEleicao mensagem) {
            return destino(urlAgencia).map(agencia -> {
                agencia.receberEleicao(mensagem.origemId(), mensagem.vetorEnvio());
                return true;
            }).orElse(false);
        }

        @Override
        public boolean enviarCoordenador(String urlAgencia, MensagemCoordenador mensagem) {
            return destino(urlAgencia).map(agencia -> {
                agencia.receberCoordenador(mensagem.liderId(), mensagem.vetorEnvio());
                return true;
            }).orElse(false);
        }

        @Override
        public Optional<LiderResponse> consultarLider(String urlAgencia) {
            return destino(urlAgencia).map(BullyService::obterLiderAtual);
        }
    }
}
