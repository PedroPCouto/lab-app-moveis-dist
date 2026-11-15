package br.com.pucminas.iceibank.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.BullyProperties;
import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;
import jakarta.annotation.PreDestroy;

@Service
public class BullyService {
    private static final Logger log = LoggerFactory.getLogger(BullyService.class);

    static final int SEM_LIDER = -1;

    private final int idAgencia;
    private final int offset;
    private final BullyProperties propriedades;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final ClienteBully cliente;

    private final AtomicInteger liderAtual = new AtomicInteger(SEM_LIDER);
    private final AtomicBoolean eleicaoEmAndamento = new AtomicBoolean(false);

    private final ExecutorService eleicoes = Executors.newSingleThreadExecutor(threadsComNome("bully-eleicao"));

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(threadsComNome("bully-heartbeat"));

    public BullyService(AgenciaConfig config, BullyProperties propriedades, RelogioVetorial relogio,
                        RegistroEventos registro, ClienteBully cliente) {
        this.idAgencia = config.getIdAgencia();
        this.offset = config.getOffset();
        this.propriedades = propriedades;
        this.relogio = relogio;
        this.registro = registro;
        this.cliente = cliente;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        if (!propriedades.habilitado()) {
            log.info("Eleicao de lider desabilitada (iceibank.bully.habilitado=false).");
            return;
        }
        eleicoes.submit(this::iniciarEleicao);
        iniciarHeartbeat(propriedades.intervaloHeartbeat().toMillis());
    }

    public void iniciarEleicao() {
        if (!eleicaoEmAndamento.compareAndSet(false, true)) {
            return;
        }
        List<Integer> responderam = new ArrayList<>();
        try {
            registro.registrar("ELEICAO_INICIADA", relogio.eventoLocal(), ContaService.detalhes(
                    "idAgencia", idAgencia, "liderConhecida", liderOuNulo()));

            for (int id : agenciasComIdMaior()) {
                int[] vetorEnvio = relogio.aoEnviar();
                registro.registrar("ELEICAO_ENVIADA", vetorEnvio, ContaService.detalhes(
                        "de", idAgencia, "para", id));
                if (cliente.enviarEleicao(url(id), new MensagemEleicao(idAgencia, vetorEnvio))) {
                    responderam.add(id);
                }
            }
        } finally {
            eleicaoEmAndamento.set(false);
        }

        if (responderam.isEmpty()) {
            tornarMeLider();
        } else {
            registro.registrar("AGUARDANDO_COORDENADOR", relogio.eventoLocal(), ContaService.detalhes(
                    "idAgencia", idAgencia, "responderam", responderam));
        }
    }

    private void tornarMeLider() {
        liderAtual.set(idAgencia);
        registro.registrar("TORNOU_LIDER", relogio.eventoLocal(), ContaService.detalhes("idAgencia", idAgencia));
        for (int id = 0; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            if (id == idAgencia) {
                continue;
            }
            int[] vetorEnvio = relogio.aoEnviar();
            registro.registrar("COORDENADOR_ENVIADO", vetorEnvio, ContaService.detalhes(
                    "liderId", idAgencia, "para", id));
            cliente.enviarCoordenador(url(id), new MensagemCoordenador(idAgencia, vetorEnvio));
        }
    }

    public void receberEleicao(int origemId, int[] vetorRecebido) {
        int[] vetor = relogio.aoReceber(vetorRecebido);
        registro.registrar("ELEICAO_RECEBIDA", vetor, ContaService.detalhes("de", origemId, "minhaId", idAgencia));
        eleicoes.submit(this::iniciarEleicao);
    }

    public void receberCoordenador(int liderId, int[] vetorRecebido) {
        int[] vetor = relogio.aoReceber(vetorRecebido);
        if (liderId < idAgencia) {
            registro.registrar("LIDER_ANUNCIADO", vetor, ContaService.detalhes(
                    "liderId", liderId, "agencia", idAgencia, "aceito", false,
                    "motivo", "id menor que o desta agencia; iniciando nova eleicao"));
            eleicoes.submit(this::iniciarEleicao);
            return;
        }
        liderAtual.set(liderId);
        registro.registrar("LIDER_ANUNCIADO", vetor, ContaService.detalhes(
                "liderId", liderId, "agencia", idAgencia, "aceito", true));
    }

    public void verificarLider() {
        int lider = liderAtual.get();
        if (lider == SEM_LIDER) {
            iniciarEleicao();
            return;
        }
        if (lider == idAgencia) {
            return;
        }

        Optional<LiderResponse> resposta = cliente.consultarLider(url(lider));
        String motivo;
        if (resposta.isEmpty()) {
            motivo = "sem resposta em " + propriedades.timeout().toMillis() + " ms";
        } else if (!Integer.valueOf(lider).equals(resposta.get().liderAtual())) {
            motivo = "respondeu, mas nao se considera lider (liderAtual=" + resposta.get().liderAtual() + ")";
        } else {
            return;
        }

        if (liderAtual.compareAndSet(lider, SEM_LIDER)) {
            registro.registrar("LIDER_NAO_RESPONDEU", relogio.eventoLocal(), ContaService.detalhes(
                    "liderAtual", lider, "agencia", idAgencia, "motivo", motivo));
            iniciarEleicao();
        }
    }

    public void iniciarHeartbeat(long intervaloMs) {
        heartbeat.scheduleWithFixedDelay(this::verificarLiderSemDerrubarOAgendamento,
                intervaloMs, intervaloMs, TimeUnit.MILLISECONDS);
    }

    private void verificarLiderSemDerrubarOAgendamento() {
        try {
            verificarLider();
        } catch (RuntimeException erro) {
            log.warn("Falha no heartbeat: {}", erro.toString());
        }
    }

    public LiderResponse obterLiderAtual() {
        return new LiderResponse(idAgencia, liderOuNulo(), eleicaoEmAndamento.get(), Instant.now().toString());
    }

    public Integer liderOuNulo() {
        int lider = liderAtual.get();
        return lider == SEM_LIDER ? null : lider;
    }

    private List<Integer> agenciasComIdMaior() {
        List<Integer> maiores = new ArrayList<>();
        for (int id = idAgencia + 1; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            maiores.add(id);
        }
        return maiores;
    }

    private String url(int id) {
        return "http://" + propriedades.hostAgencias() + ":" + AgenciaConfig.portaDaAgencia(offset, id);
    }

    @PreDestroy
    public void encerrar() throws InterruptedException {
        heartbeat.shutdownNow();
        eleicoes.shutdownNow();
        eleicoes.awaitTermination(2, TimeUnit.SECONDS);
    }

    private static ThreadFactory threadsComNome(String nome) {
        return tarefa -> {
            Thread thread = new Thread(tarefa, nome);
            thread.setDaemon(true);
            return thread;
        };
    }
}
