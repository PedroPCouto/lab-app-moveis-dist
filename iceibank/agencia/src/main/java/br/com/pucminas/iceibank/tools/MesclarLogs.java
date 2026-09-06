package br.com.pucminas.iceibank.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import br.com.pucminas.iceibank.model.Evento;

public final class MesclarLogs {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private MesclarLogs() {
    }

    public static void main(String[] args) throws IOException {
        Path pastaDados = Path.of(args.length > 0 ? args[0] : "data");

        if (!Files.isDirectory(pastaDados)) {
            System.err.println("Pasta de dados nao encontrada: " + pastaDados.toAbsolutePath());
            System.err.println("Rode as agencias e gere alguns eventos antes de mesclar os logs.");
            System.exit(1);
        }

        List<Evento> todosEventos = lerEventos(pastaDados);
        if (todosEventos.isEmpty()) {
            System.out.println("Nenhum evento registrado ainda em " + pastaDados.toAbsolutePath());
            return;
        }

        todosEventos.sort(Comparator.comparingInt(Evento::timestampLamport)
                .thenComparing(Evento::agencia));

        System.out.println("=== Linha do tempo unificada (ordenada por relogio de Lamport) ===");
        for (int i = 0; i < todosEventos.size(); i++) {
            Evento evento = todosEventos.get(i);
            System.out.printf("[Lamport %3d] (%s) %s - %-30s %s%s%n",
                    evento.timestampLamport(),
                    evento.horaParede(),
                    evento.agencia(),
                    evento.tipo(),
                    MAPPER.writeValueAsString(evento.detalhes()),
                    empataComVizinho(todosEventos, i) ? "   <-- EMPATE" : "");
        }

        imprimirResumo(todosEventos);
    }

    private static List<Evento> lerEventos(Path pastaDados) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pastaDados)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (!linha.isBlank()) {
                        eventos.add(MAPPER.readValue(linha, Evento.class));
                    }
                }
            }
        }
        return eventos;
    }

    private static boolean empataComVizinho(List<Evento> eventos, int indice) {
        Evento atual = eventos.get(indice);
        return (indice > 0 && empatam(atual, eventos.get(indice - 1)))
                || (indice < eventos.size() - 1 && empatam(atual, eventos.get(indice + 1)));
    }

    private static boolean empatam(Evento a, Evento b) {
        return a.timestampLamport() == b.timestampLamport() && !a.agencia().equals(b.agencia());
    }

    private static void imprimirResumo(List<Evento> eventos) {
        long empates = 0;
        for (int i = 0; i < eventos.size(); i++) {
            if (empataComVizinho(eventos, i)) {
                empates++;
            }
        }
        System.out.println();
        System.out.println("=== Resumo ===");
        System.out.println("Eventos no total .................. " + eventos.size());
        System.out.println("Maior timestamp de Lamport ........ "
                + eventos.get(eventos.size() - 1).timestampLamport());
        System.out.println("Eventos empatados entre agencias .. " + empates
                + "  (o relogio de Lamport nao os ordena entre si)");
    }
}
