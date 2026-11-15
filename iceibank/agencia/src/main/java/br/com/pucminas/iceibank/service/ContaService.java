package br.com.pucminas.iceibank.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.dto.ContaComVetor;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.exception.ConflitoSincronizacaoException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;
import br.com.pucminas.iceibank.model.Evento;

@Service
public class ContaService {
    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();
    private final AgenciaConfig config;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;

    public ContaService(AgenciaConfig config, RelogioVetorial relogio, RegistroEventos registro) {
        this.config = config;
        this.relogio = relogio;
        this.registro = registro;
    }

    public Conta criar(int id, String nomeAluno, BigDecimal saldoInicial, Autenticacao auth) {
        if (AgenciaConfig.agenciaResponsavel(id) != config.getIdAgencia()) {
            throw ApiException.requisicaoInvalida("Conta " + id + " nao pertence a esta agencia. "
                    + "Responsavel: agencia " + AgenciaConfig.agenciaResponsavel(id) + ".");
        }
        if (contas.containsKey(id)) {
            throw ApiException.conflito("Conta ja existe.");
        }

        String nome = (nomeAluno == null || nomeAluno.isBlank()) ? auth.nome() : nomeAluno;
        Conta conta = new Conta(id, nome, auth.sujeito(), saldoInicial);

        int[] ts = relogio.eventoLocal();
        contas.put(id, conta);
        registro.registrar("CRIAR_CONTA", ts, detalhes(
                "id", id, "nomeAluno", nome, "dono", auth.sujeito(), "saldoInicial", conta.getSaldo()));

        return conta;
    }

    public Conta buscar(int id) {
        return procurar(id).orElseThrow(() -> ApiException.naoEncontrado("Conta nao encontrada nesta agencia."));
    }

    public Optional<Conta> procurar(int id) {
        return Optional.ofNullable(contas.get(id));
    }

    public Conta buscarDoUsuario(int id, Autenticacao auth) {
        Conta conta = buscar(id);
        if (!conta.getDono().equals(auth.sujeito())) {
            throw ApiException.proibido("Conta " + id + " nao pertence ao usuario autenticado.");
        }
        return conta;
    }

    public List<Conta> contasDoUsuario(Autenticacao auth) {
        return contas.values().stream()
                .filter(c -> c.getDono().equals(auth.sujeito()))
                .sorted(Comparator.comparingInt(Conta::getId))
                .toList();
    }

    public Conta depositar(int id, BigDecimal valor, Autenticacao auth) {
        Conta conta = buscarDoUsuario(id, auth);
        synchronized (conta) {
            int[] ts = relogio.eventoLocal();
            conta.creditar(valor);
            registro.registrar("DEPOSITO", ts, detalhes(
                    "id", id, "valor", valor, "novoSaldo", conta.getSaldo()));
        }
        return conta;
    }

    public Conta sacar(int id, BigDecimal valor, Autenticacao auth) {
        Conta conta = buscarDoUsuario(id, auth);
        synchronized (conta) {
            if (!conta.temSaldo(valor)) {
                throw ApiException.requisicaoInvalida("Saldo insuficiente.");
            }
            int[] ts = relogio.eventoLocal();
            conta.debitar(valor);
            registro.registrar("SAQUE", ts, detalhes(
                    "id", id, "valor", valor, "novoSaldo", conta.getSaldo()));
        }
        return conta;
    }

    // ---- Sprint 3, Parte E: sincronizacao offline-first -------------------------------

    /**
     * Saldo e vetor lidos juntos, com a conta travada: toda operacao que muda o saldo desta
     * conta incrementa o vetor sob a mesma trava, entao o par nunca mistura "saldo de antes"
     * com "vetor de depois" (ou vice-versa) para esta conta.
     */
    public ContaComVetor comVetor(Conta conta) {
        synchronized (conta) {
            return ContaComVetor.de(conta, relogio.valorAtual());
        }
    }

    public List<ContaComVetor> contasDoUsuarioComVetor(Autenticacao auth) {
        return contasDoUsuario(auth).stream().map(this::comVetor).toList();
    }

    /**
     * Compara o vetor que o app conhecia com o vetor atual da agencia (secao 9.3). Qualquer
     * relacao diferente de IGUAIS e conflito. Deve ser chamado com a conta travada.
     */
    public void verificarVetor(Conta conta, int[] vetorConhecido) {
        validarTamanho(vetorConhecido);
        int[] atual = relogio.valorAtual();
        RelogioVetorial.Relacao relacao = RelogioVetorial.comparar(vetorConhecido, atual);
        if (relacao != RelogioVetorial.Relacao.IGUAIS) {
            throw new ConflitoSincronizacaoException(vetorConhecido, atual, relacao, comVetor(conta));
        }
    }

    /**
     * O evento local da operacao sincronizada, so se nada aconteceu na agencia desde a
     * {@link #verificarVetor} (compare-and-increment no relogio). Com a conta travada.
     */
    public int[] eventoLocalSeNadaMudou(Conta conta, int[] vetorConhecido) {
        return relogio.eventoLocalSeIgual(vetorConhecido).orElseThrow(() -> {
            int[] atual = relogio.valorAtual();
            return new ConflitoSincronizacaoException(vetorConhecido, atual,
                    RelogioVetorial.comparar(vetorConhecido, atual), comVetor(conta));
        });
    }

    /**
     * Deposito ou saque feito offline pelo app. Ordem das verificacoes: conflito primeiro
     * (se o estado mudou, nem o saldo que o app viu vale mais), depois saldo suficiente.
     * Devolve o saldo e o vetor exatamente do instante da operacao.
     */
    public ContaComVetor aplicarSincronizado(Conta conta, String tipo, BigDecimal valor, int[] vetorConhecido,
                                             String idOperacao) {
        synchronized (conta) {
            verificarVetor(conta, vetorConhecido);
            boolean deposito = "DEPOSITO".equals(tipo);
            if (!deposito && !conta.temSaldo(valor)) {
                throw ApiException.requisicaoInvalida("Saldo insuficiente.");
            }
            int[] ts = eventoLocalSeNadaMudou(conta, vetorConhecido);
            if (deposito) {
                conta.creditar(valor);
            } else {
                conta.debitar(valor);
            }
            registro.registrar("SINCRONIZACAO_OFFLINE", ts, detalhes(
                    "id", conta.getId(), "tipo", tipo, "valor", valor, "idOperacao", idOperacao,
                    "vetorConhecido", Arrays.stream(vetorConhecido).boxed().toList(),
                    "novoSaldo", conta.getSaldo()));
            return ContaComVetor.de(conta, ts);
        }
    }

    private void validarTamanho(int[] vetor) {
        if (vetor == null || vetor.length != AgenciaConfig.NUMERO_AGENCIAS) {
            throw ApiException.requisicaoInvalida("vetorConhecido deve ter " + AgenciaConfig.NUMERO_AGENCIAS
                    + " posicoes (uma por agencia).");
        }
    }

    public List<Evento> historico(int id, int limite) {
        return registro.eventos().reversed().stream()
                .filter(evento -> envolveConta(evento, id))
                .limit(limite)
                .toList();
    }

    private boolean envolveConta(Evento evento, int id) {
        Map<String, Object> detalhes = evento.detalhes();
        return List.of("id", "idConta", "idOrigem", "idDestino").stream()
                .map(detalhes::get)
                .anyMatch(valor -> valor instanceof Number numero && numero.intValue() == id);
    }

    public int quantidade() {
        return contas.size();
    }

    public List<Integer> idsDasContas() {
        return contas.keySet().stream().sorted().toList();
    }

    static Map<String, Object> detalhes(Object... paresChaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            mapa.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        return mapa;
    }
}
