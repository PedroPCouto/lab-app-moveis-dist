package br.com.pucminas.iceibank.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

        todosEventos.sort(Comparator.comparing(Evento::horaParede));

        System.out.println("=== Linha do tempo (ordenada por hora de parede) ===");
        for (Evento evento : todosEventos) {
            System.out.printf("[%s] vetor=%-10s %-30s %s%n",
                    evento.agencia(),
                    Arrays.toString(evento.timestampVetorial()).replace(" ", ""),
                    evento.tipo(),
                    MAPPER.writeValueAsString(evento.detalhes()));
        }
    }

    private static List<Evento> lerEventos(Path pastaDados) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pastaDados)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    Evento evento = MAPPER.readValue(linha, Evento.class);
                    if (evento.timestampVetorial() != null) {
                        eventos.add(evento);
                    }
                }
            }
        }
        return eventos;
    }
}
