package br.com.pucminas.iceibank.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.exception.ApiException;
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
