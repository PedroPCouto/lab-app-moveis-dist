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

/**
 * Sprint 3, Partes A e B: eleicao de lider pelo algoritmo de Bully (Garcia-Molina, 1982)
 * e heartbeat para detectar a queda da lider. Vence sempre a agencia de MAIOR id entre as
 * que estao respondendo.
 *
 * <p>Traducao para Java do {@code bullyService.js} do roteiro, com tres cuidados que o
 * exemplo em Node nao precisava ter (ou deixava passar):
 * <ul>
 *   <li><b>Concorrencia.</b> Requisicoes HTTP chegam em threads do Tomcat enquanto a eleicao
 *   roda em outra thread. {@code liderAtual} e um {@link AtomicInteger} e "ja existe uma
 *   eleicao em andamento" e um {@link AtomicBoolean} trocado com compareAndSet - duas
 *   threads nunca iniciam a mesma eleicao ao mesmo tempo.</li>
 *   <li><b>O "OK" volta na hora.</b> Ao receber uma eleicao, a agencia responde 200 e so
 *   depois conduz a propria eleicao, em outra thread. Se ela conduzisse a eleicao antes de
 *   responder (ate 1 s por agencia maior), quem perguntou estouraria o timeout e se
 *   declararia lider por engano.</li>
 *   <li><b>Anuncio de quem tem id menor.</b> Se uma agencia de id menor se anuncia lider
 *   (ela nao me viu a tempo, por exemplo porque eu ainda estava subindo), eu nao aceito:
 *   inicio uma eleicao, que eu vou vencer. Sem isso, a "tempestade" da subida pode convergir
 *   para a agencia errada - exatamente o que o Bully promete evitar.</li>
 * </ul>
 *
 * <p>Toda mensagem do Bully passa pelo relogio vetorial (regras de envio e recebimento do
 * Sprint 2) e vira um evento no log da agencia, de onde o {@code HistoricoLideranca}
 * reconstroi a linha do tempo (Parte C).
 */
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

    // Eleicoes disparadas por mensagens recebidas rodam fora da thread do Tomcat (ver acima).
    private final ExecutorService eleicoes = Executors.newSingleThreadExecutor(threadsComNome("bully-eleicao"));

    // Parte B: uma thread so para o heartbeat (o setInterval do exemplo em Node).
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

    /** Equivale a chamar {@code bully.iniciarEleicao()} dentro do {@code app.listen} do roteiro. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        if (!propriedades.habilitado()) {
            log.info("Eleicao de lider desabilitada (iceibank.bully.habilitado=false).");
            return;
        }
        eleicoes.submit(this::iniciarEleicao);
        iniciarHeartbeat(propriedades.intervaloHeartbeat().toMillis());
    }

    /**
     * Passos 1 e 2 do Bully: pergunta a todas as agencias de id maior; se nenhuma responder,
     * esta agencia vence. Se alguma responder, e ela quem conduz a eleicao dali em diante e
     * anuncia o resultado - esta agencia so espera o {@code /bully/coordenador}.
     */
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
                // sem resposta: agencia fora do ar (ou lenta demais) - segue para a proxima
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
            // agencia fora do ar: descobre a lider quando voltar (ela faz a propria eleicao ao subir)
        }
    }

    /**
     * Passo 4: quem tem id menor perguntou. O 200 da rota ja e o "OK" (estou viva); a
     * propria eleicao roda em seguida, em outra thread, porque esta agencia tambem e
     * candidata - e tem id maior que quem chamou.
     */
    public void receberEleicao(int origemId, int[] vetorRecebido) {
        int[] vetor = relogio.aoReceber(vetorRecebido);
        registro.registrar("ELEICAO_RECEBIDA", vetor, ContaService.detalhes("de", origemId, "minhaId", idAgencia));
        eleicoes.submit(this::iniciarEleicao);
    }

    public void receberCoordenador(int liderId, int[] vetorRecebido) {
        int[] vetor = relogio.aoReceber(vetorRecebido);
        if (liderId < idAgencia) {
            // Quem se anunciou nao me viu a tempo. Pelo Bully, a lider tem que ser a maior
            // ativa - e eu estou ativa: em vez de aceitar, faco a minha eleicao.
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

    /**
     * Parte B: confere se a lider conhecida continua respondendo. Sem lider conhecida (nunca
     * houve, ou uma eleicao anterior ficou sem anuncio), faz uma eleicao. Se a lider nao
     * responder dentro do timeout - ou responder que ja nao se considera lider, o que
     * acontece quando ela reiniciou e esta no meio da propria eleicao -, esquece a lider
     * antiga e faz uma eleicao.
     */
    public void verificarLider() {
        int lider = liderAtual.get();
        if (lider == SEM_LIDER) {
            iniciarEleicao();
            return;
        }
        if (lider == idAgencia) {
            return; // eu sou a lider, nada a verificar
        }

        Optional<LiderResponse> resposta = cliente.consultarLider(url(lider));
        String motivo;
        if (resposta.isEmpty()) {
            motivo = "sem resposta em " + propriedades.timeout().toMillis() + " ms";
        } else if (!Integer.valueOf(lider).equals(resposta.get().liderAtual())) {
            motivo = "respondeu, mas nao se considera lider (liderAtual=" + resposta.get().liderAtual() + ")";
        } else {
            return; // a lider respondeu e confirma: tudo certo
        }

        // So esquece a lider se ninguem trocou a lider enquanto a consulta estava em curso.
        if (liderAtual.compareAndSet(lider, SEM_LIDER)) {
            registro.registrar("LIDER_NAO_RESPONDEU", relogio.eventoLocal(), ContaService.detalhes(
                    "liderAtual", lider, "agencia", idAgencia, "motivo", motivo));
            iniciarEleicao();
        }
    }

    /**
     * Agenda {@link #verificarLider()} a cada {@code intervaloMs}. Com atraso fixo (e nao taxa
     * fixa): a proxima verificacao so comeca {@code intervaloMs} depois de a anterior terminar,
     * entao uma verificacao lenta (timeout + eleicao) nunca se sobrepoe a seguinte - o que o
     * setInterval do Node permitiria.
     */
    public void iniciarHeartbeat(long intervaloMs) {
        heartbeat.scheduleWithFixedDelay(this::verificarLiderSemDerrubarOAgendamento,
                intervaloMs, intervaloMs, TimeUnit.MILLISECONDS);
    }

    // Uma excecao dentro de uma tarefa agendada cancela as execucoes seguintes em silencio.
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
