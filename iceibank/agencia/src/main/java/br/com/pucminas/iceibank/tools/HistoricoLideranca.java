package br.com.pucminas.iceibank.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import br.com.pucminas.iceibank.model.Evento;

public final class HistoricoLideranca {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final List<String> TIPOS_BULLY = List.of(
            "ELEICAO_INICIADA", "ELEICAO_RECEBIDA", "TORNOU_LIDER", "LIDER_ANUNCIADO", "LIDER_NAO_RESPONDEU");

    private HistoricoLideranca() {
    }

    public static void main(String[] args) throws IOException {
        Path pastaDados = Path.of(args.length > 0 ? args[0] : "data");
        if (!Files.isDirectory(pastaDados)) {
            System.err.println("Pasta de dados nao encontrada: " + pastaDados.toAbsolutePath());
            System.exit(1);
        }

        List<Evento> eventosBully = lerEventos(pastaDados).stream()
                .filter(e -> TIPOS_BULLY.contains(e.tipo()))
                .sorted(Comparator.comparing(Evento::horaParede))
                .toList();
        if (eventosBully.isEmpty()) {
            System.out.println("Nenhum evento de eleicao em " + pastaDados.toAbsolutePath()
                    + " - suba as agencias e rode de novo.");
            return;
        }

        System.out.println("=== Historico de eventos de eleicao ===");
        for (Evento evento : eventosBully) {
            System.out.printf("(%s) [%s] %-19s %-11s %s%n", hora(evento), evento.agencia(), evento.tipo(),
                    Arrays.toString(evento.timestampVetorial()).replace(" ", ""),
                    MAPPER.writeValueAsString(evento.detalhes()));
        }

        System.out.println();
        System.out.println("=== Linha do tempo de lideres ===");
        Object liderVigente = null;
        Object ultimaQuedaAnunciada = null;
        int declaracoes = 0;
        int trocas = 0;
        for (Evento evento : eventosBully) {
            if (evento.tipo().equals("TORNOU_LIDER")) {
                declaracoes++;
                Object lider = evento.detalhes().get("idAgencia");
                if (!Objects.equals(lider, liderVigente)) {
                    trocas++;
                    System.out.printf("A partir de %s: Agencia %s e a lider%s%n", evento.horaParede(), lider,
                            liderVigente == null ? " (lider inicial)" : " (antes: agencia " + liderVigente + ")");
                    liderVigente = lider;
                    ultimaQuedaAnunciada = null;
                }
            } else if (evento.tipo().equals("LIDER_NAO_RESPONDEU")) {
                Object caiu = evento.detalhes().get("liderAtual");
                if (Objects.equals(caiu, liderVigente) && !Objects.equals(caiu, ultimaQuedaAnunciada)) {
                    System.out.printf("Em %s: agencia %s detectou que a lider %s parou de responder (%s)%n",
                            evento.horaParede(), evento.detalhes().get("agencia"), caiu,
                            evento.detalhes().get("motivo"));
                    ultimaQuedaAnunciada = caiu;
                }
            }
        }

        System.out.println();
        System.out.println("=== Resumo ===");
        System.out.println("Eventos de eleicao ................................ " + eventosBully.size());
        System.out.println("Declaracoes TORNOU_LIDER (inclui reafirmacoes) .... " + declaracoes);
        System.out.println("Trocas efetivas de lideranca (lider diferente) .... " + trocas);
        System.out.println("Lider ao final dos logs ........................... agencia " + liderVigente);
    }

    private static String hora(Evento evento) {
        String parede = evento.horaParede();
        int t = parede.indexOf('T');
        return t >= 0 && parede.length() >= t + 13 ? parede.substring(t + 1, t + 13) : parede;
    }

    private static List<Evento> lerEventos(Path pastaDados) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pastaDados)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    try {
                        Evento evento = MAPPER.readValue(linha, Evento.class);
                        if (evento.tipo() != null && evento.horaParede() != null) {
                            eventos.add(evento);
                        }
                    } catch (RuntimeException formatoAntigo) {
                    }
                }
            }
        }
        return eventos;
    }
}
