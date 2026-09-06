package br.com.pucminas.iceibank.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.dto.CreditoRemotoRequest;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;
import br.com.pucminas.iceibank.security.JwtService;

@Service
public class TransferenciaService {
    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final ContaService contaService;
    private final AgenciaConfig config;
    private final RelogioLamport relogio;
    private final RegistroEventos registro;
    private final RestClient restClient;
    private final JwtService jwtService;

    public TransferenciaService(ContaService contaService, AgenciaConfig config, RelogioLamport relogio,
                                RegistroEventos registro, RestClient restClient, JwtService jwtService) {
        this.contaService = contaService;
        this.config = config;
        this.relogio = relogio;
        this.registro = registro;
        this.restClient = restClient;
        this.jwtService = jwtService;
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

        int tsDebito;
        synchronized (contaOrigem) {
            if (!contaOrigem.temSaldo(valor)) {
                throw ApiException.requisicaoInvalida("Saldo insuficiente.");
            }
            tsDebito = relogio.eventoLocal();
            contaOrigem.debitar(valor);
            registro.registrar("TRANSFERENCIA_DEBITO", tsDebito, ContaService.detalhes(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "agenciaDestino", agenciaDestino, "novoSaldo", contaOrigem.getSaldo()));
        }

        return mesmaAgencia
                ? creditarLocal(idOrigem, idDestino, valor, contaOrigem)
                : creditarEmOutraAgencia(idOrigem, idDestino, valor, agenciaDestino, contaOrigem);
    }

    private TransferenciaResponse creditarLocal(int idOrigem, int idDestino, BigDecimal valor, Conta contaOrigem) {
        Conta contaDestino = contaService.buscar(idDestino);
        int tsCredito;
        synchronized (contaDestino) {
            tsCredito = relogio.eventoLocal();
            contaDestino.creditar(valor);
            registro.registrar("TRANSFERENCIA_CREDITO", tsCredito, ContaService.detalhes(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "novoSaldo", contaDestino.getSaldo()));
        }
        return new TransferenciaResponse("Transferencia concluida (mesma agencia).", "LOCAL",
                idOrigem, idDestino, valor, contaOrigem.getSaldo(), config.getIdAgencia(), tsCredito);
    }

    private TransferenciaResponse creditarEmOutraAgencia(int idOrigem, int idDestino, BigDecimal valor,
                                                         int agenciaDestino, Conta contaOrigem) {
        int tsEnvio = relogio.aoEnviar();
        String urlDestino = config.urlDe(agenciaDestino);

        try {
            restClient.post()
                    .uri(urlDestino + "/contas/{id}/creditar-remoto", idDestino)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.gerarTokenInterno())
                    .body(new CreditoRemotoRequest(valor, tsEnvio, config.getIdAgencia()))
                    .retrieve()
                    .toBodilessEntity();

            return new TransferenciaResponse("Transferencia concluida (entre agencias).", "ENTRE_AGENCIAS",
                    idOrigem, idDestino, valor, contaOrigem.getSaldo(), agenciaDestino, tsEnvio);
        } catch (Exception erro) {
            log.warn("Falha ao creditar conta {} na agencia {}: {}", idDestino, agenciaDestino, erro.toString());
            registro.registrar("TRANSFERENCIA_FALHOU", relogio.eventoLocal(), ContaService.detalhes(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "agenciaDestino", agenciaDestino, "erro", erro.getMessage(),
                    "inconsistencia", "debito aplicado sem credito correspondente"));
            throw ApiException.agenciaIndisponivel(
                    "Falha ao contatar agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4).");
        }
    }

    public Conta creditarRemoto(int idConta, BigDecimal valor, int timestampRecebido, int origemAgencia) {
        int ts = relogio.aoReceber(timestampRecebido);

        Conta conta = contaService.buscar(idConta);
        synchronized (conta) {
            conta.creditar(valor);
            registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", ts, ContaService.detalhes(
                    "idConta", idConta, "valor", valor, "origemAgencia", origemAgencia,
                    "timestampRecebido", timestampRecebido, "novoSaldo", conta.getSaldo()));
        }
        return conta;
    }
}
