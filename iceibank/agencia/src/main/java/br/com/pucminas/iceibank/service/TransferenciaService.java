package br.com.pucminas.iceibank.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.MensageriaConfig;
import br.com.pucminas.iceibank.dto.MensagemCredito;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;

@Service
public class TransferenciaService {
    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final ContaService contaService;
    private final AgenciaConfig config;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final Mensageria mensageria;

    public TransferenciaService(ContaService contaService, AgenciaConfig config, RelogioVetorial relogio,
                                RegistroEventos registro, Mensageria mensageria) {
        this.contaService = contaService;
        this.config = config;
        this.relogio = relogio;
        this.registro = registro;
        this.mensageria = mensageria;
    }

    public TransferenciaResponse transferir(int idOrigem, int idDestino, BigDecimal valor, Autenticacao auth) {
        if (idOrigem == idDestino) {
            throw ApiException.requisicaoInvalida("Conta de origem e destino sao a mesma.");
        }

        Conta contaOrigem = contaService.buscarDoUsuario(idOrigem, auth);

        int agenciaDestino = AgenciaConfig.agenciaResponsavel(idDestino);
        boolean mesmaAgencia = agenciaDestino == config.getIdAgencia();

        if (mesmaAgencia && !contaService.idsDasContas().contains(idDestino)) {
            throw ApiException.naoEncontrado("Conta de destino nao encontrada.");
        }

        String idTransferencia = UUID.randomUUID().toString();

        synchronized (contaOrigem) {
            if (!contaOrigem.temSaldo(valor)) {
                throw ApiException.requisicaoInvalida("Saldo insuficiente.");
            }
            int[] tsDebito = relogio.eventoLocal();
            contaOrigem.debitar(valor);
            registro.registrar("TRANSFERENCIA_DEBITO", tsDebito, ContaService.detalhes(
                    "idTransferencia", idTransferencia, "idOrigem", idOrigem, "idDestino", idDestino,
                    "valor", valor, "agenciaDestino", agenciaDestino, "novoSaldo", contaOrigem.getSaldo()));
        }

        return mesmaAgencia
                ? creditarLocal(idTransferencia, idOrigem, idDestino, valor, contaOrigem)
                : publicarCredito(idTransferencia, idOrigem, idDestino, valor, agenciaDestino, contaOrigem);
    }

    private TransferenciaResponse creditarLocal(String idTransferencia, int idOrigem, int idDestino,
                                                BigDecimal valor, Conta contaOrigem) {
        Conta contaDestino = contaService.buscar(idDestino);
        int[] tsCredito;
        synchronized (contaDestino) {
            tsCredito = relogio.eventoLocal();
            contaDestino.creditar(valor);
            registro.registrar("TRANSFERENCIA_CREDITO", tsCredito, ContaService.detalhes(
                    "idTransferencia", idTransferencia, "idOrigem", idOrigem, "idDestino", idDestino,
                    "valor", valor, "novoSaldo", contaDestino.getSaldo()));
        }
        return new TransferenciaResponse("Transferencia concluida (mesma agencia).", "LOCAL",
                idOrigem, idDestino, valor, contaOrigem.getSaldo(), config.getIdAgencia(), tsCredito,
                idTransferencia);
    }

    // Sprint 1 chamava POST /contas/{id}/creditar-remoto na outra agencia. Agora o credito
    // vira uma mensagem na exchange: a agencia de destino consome quando puder, mesmo que
    // esteja fora do ar neste momento (a fila e duravel e a mensagem, persistente).
    private TransferenciaResponse publicarCredito(String idTransferencia, int idOrigem, int idDestino,
                                                  BigDecimal valor, int agenciaDestino, Conta contaOrigem) {
        String routingKey = MensageriaConfig.routingKeyCredito(agenciaDestino);
        int[] vetorEnvio = relogio.aoEnviar();
        MensagemCredito mensagem = new MensagemCredito(
                idTransferencia, idOrigem, idDestino, valor, vetorEnvio, config.getIdAgencia());

        try {
            mensageria.publicar(routingKey, mensagem, idTransferencia);
        } catch (AmqpException erro) {
            log.warn("Falha ao publicar credito da transferencia {}: {}", idTransferencia, erro.toString());
            estornar(idTransferencia, contaOrigem, valor, erro);
            throw ApiException.mensageriaIndisponivel(
                    "Nao foi possivel publicar a transferencia no RabbitMQ. O debito foi estornado.");
        }

        registro.registrar("TRANSFERENCIA_ENVIADA", vetorEnvio, ContaService.detalhes(
                "idTransferencia", idTransferencia, "idOrigem", idOrigem, "idDestino", idDestino,
                "valor", valor, "agenciaDestino", agenciaDestino, "routingKey", routingKey));

        // 200 aqui quer dizer "o broker aceitou a mensagem", nao "o credito ja foi aplicado".
        return new TransferenciaResponse(
                "Transferencia publicada para a agencia de destino (entrega assincrona).", "ENTRE_AGENCIAS",
                idOrigem, idDestino, valor, contaOrigem.getSaldo(), agenciaDestino, vetorEnvio, idTransferencia);
    }

    // A mensagem nao saiu da agencia, entao desfazer o debito localmente e seguro: ninguem
    // mais viu esse dinheiro. (Bem diferente do Sprint 1, em que o credito remoto podia ter
    // sido aplicado mesmo com erro na resposta HTTP.)
    private void estornar(String idTransferencia, Conta contaOrigem, BigDecimal valor, Exception motivo) {
        synchronized (contaOrigem) {
            int[] ts = relogio.eventoLocal();
            contaOrigem.creditar(valor);
            registro.registrar("TRANSFERENCIA_ESTORNADA", ts, ContaService.detalhes(
                    "idTransferencia", idTransferencia, "idOrigem", contaOrigem.getId(), "valor", valor,
                    "novoSaldo", contaOrigem.getSaldo(), "motivo", String.valueOf(motivo.getMessage())));
        }
    }

    public int reprocessarCreditosMortos() {
        int reprocessadas = mensageria.reprocessarMortas(config.getIdAgencia());
        registro.registrar("MENSAGENS_MORTAS_REPROCESSADAS", relogio.eventoLocal(), ContaService.detalhes(
                "fila", MensageriaConfig.nomeFilaMortas(config.getIdAgencia()), "quantidade", reprocessadas));
        return reprocessadas;
    }

    /**
     * Aplica um credito vindo de outra agencia pelo RabbitMQ. Devolve {@code false} quando
     * a conta nao existe aqui - por exemplo, porque a agencia reiniciou e perdeu as contas
     * em memoria antes de consumir a mensagem (cenario da Parte C).
     */
    public boolean creditarRemoto(MensagemCredito mensagem) {
        int[] ts = relogio.aoReceber(mensagem.vetorEnvio());

        Optional<Conta> encontrada = contaService.procurar(mensagem.idConta());
        if (encontrada.isEmpty()) {
            registro.registrar("CREDITO_REMOTO_FALHOU", ts, ContaService.detalhes(
                    "idTransferencia", mensagem.idTransferencia(), "idConta", mensagem.idConta(),
                    "valor", mensagem.valor(), "origemAgencia", mensagem.origemAgencia(),
                    "vetorRecebido", comoLista(mensagem.vetorEnvio()), "motivo", "conta nao encontrada"));
            return false;
        }

        Conta conta = encontrada.get();
        synchronized (conta) {
            conta.creditar(mensagem.valor());
            registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", ts, ContaService.detalhes(
                    "idTransferencia", mensagem.idTransferencia(), "idConta", mensagem.idConta(),
                    "valor", mensagem.valor(), "origemAgencia", mensagem.origemAgencia(),
                    "vetorRecebido", comoLista(mensagem.vetorEnvio()), "novoSaldo", conta.getSaldo()));
        }
        return true;
    }

    // Lista em vez de int[] nos detalhes: o log de console usa toString(), e int[] sairia "[I@1b67bb0f".
    private static List<Integer> comoLista(int[] vetor) {
        return Arrays.stream(vetor).boxed().toList();
    }
}
