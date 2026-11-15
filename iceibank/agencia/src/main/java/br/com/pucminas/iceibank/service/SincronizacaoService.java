package br.com.pucminas.iceibank.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.dto.ContaComVetor;
import br.com.pucminas.iceibank.dto.SincronizacaoRequest;
import br.com.pucminas.iceibank.dto.SincronizacaoResponse;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.exception.ConflitoSincronizacaoException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;

/**
 * Sprint 3, Parte E: aplica operacoes que o app fez offline, so se o estado da agencia nao
 * mudou desde a ultima leitura do app (vetor conhecido == vetor atual). Caso contrario,
 * 409 com o estado real, e quem decide o que fazer e o app/a pessoa usuaria.
 *
 * <p>Alem do exemplo do roteiro:
 * <ul>
 *   <li>transferencias tambem (a comparacao e a mesma; muda so o que se aplica);</li>
 *   <li>idempotencia por {@code idOperacao}: se a resposta de uma sincronizacao se perde
 *   (a rede caiu de novo no meio), o app reenvia; sem isso, o reenvio daria 409 - o vetor
 *   ja andou por causa da propria operacao - e a pessoa veria "conflito" para uma operacao
 *   que na verdade ja foi aplicada. Com o id, o reenvio devolve a resposta original.</li>
 * </ul>
 * O registro de ids vive em memoria, como as contas: reiniciar a agencia apaga os dois.
 */
@Service
public class SincronizacaoService {
    private static final Logger log = LoggerFactory.getLogger(SincronizacaoService.class);

    static final List<String> TIPOS = List.of("DEPOSITO", "SAQUE", "TRANSFERENCIA");

    private final ContaService contaService;
    private final TransferenciaService transferenciaService;
    private final Map<String, SincronizacaoResponse> aplicadas = new ConcurrentHashMap<>();

    public SincronizacaoService(ContaService contaService, TransferenciaService transferenciaService) {
        this.contaService = contaService;
        this.transferenciaService = transferenciaService;
    }

    public SincronizacaoResponse sincronizar(int id, SincronizacaoRequest req, Autenticacao auth) {
        String tipo = req.tipo().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) {
            throw ApiException.requisicaoInvalida("Tipo de operacao pendente nao suportado: " + req.tipo()
                    + ". Use " + String.join(", ", TIPOS) + ".");
        }
        if (tipo.equals("TRANSFERENCIA") && req.idDestino() == null) {
            throw ApiException.requisicaoInvalida("idDestino e obrigatorio para TRANSFERENCIA.");
        }

        Conta conta = contaService.buscarDoUsuario(id, auth);
        String chave = req.idOperacao() == null || req.idOperacao().isBlank()
                ? null : auth.sujeito() + ":" + req.idOperacao();

        // A trava da conta serializa duas sincronizacoes da mesma operacao chegando juntas.
        synchronized (conta) {
            SincronizacaoResponse anterior = chave == null ? null : aplicadas.get(chave);
            if (anterior != null) {
                return anterior.comoDuplicada();
            }
            SincronizacaoResponse resposta;
            try {
                resposta = tipo.equals("TRANSFERENCIA")
                        ? transferir(conta, req, auth)
                        : aplicar(conta, tipo, req);
            } catch (ConflitoSincronizacaoException conflito) {
                log.info("Sincronizacao recusada (conta {}, {} {}): vetor conhecido {} x atual {} -> {}",
                        id, tipo, req.valor(), java.util.Arrays.toString(conflito.getVetorConhecido()),
                        java.util.Arrays.toString(conflito.getVetorAtual()), conflito.getRelacao());
                throw conflito;
            }
            if (chave != null) {
                aplicadas.put(chave, resposta);
            }
            return resposta;
        }
    }

    private SincronizacaoResponse aplicar(Conta conta, String tipo, SincronizacaoRequest req) {
        ContaComVetor depois = contaService.aplicarSincronizado(
                conta, tipo, req.valor(), req.vetorConhecido(), req.idOperacao());
        return new SincronizacaoResponse("Operacao offline aplicada.", req.idOperacao(), tipo, false, depois, null);
    }

    private SincronizacaoResponse transferir(Conta conta, SincronizacaoRequest req, Autenticacao auth) {
        TransferenciaResponse transferencia = transferenciaService.transferir(
                conta.getId(), req.idDestino(), req.valor(), auth, req.vetorConhecido());
        return new SincronizacaoResponse("Transferencia offline aplicada.", req.idOperacao(), "TRANSFERENCIA",
                false, contaService.comVetor(conta), transferencia);
    }
}
