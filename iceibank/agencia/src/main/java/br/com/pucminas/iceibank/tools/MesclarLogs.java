package br.com.pucminas.iceibank.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import br.com.pucminas.iceibank.model.Evento;
import br.com.pucminas.iceibank.service.RelogioVetorial;
import br.com.pucminas.iceibank.service.RelogioVetorial.Relacao;

/**
 * Parte D: mescla os logs das agencias e, comparando os vetores, aponta quais pares de
 * eventos de agencias diferentes sao comprovadamente concorrentes. Para cada transferencia
 * entre agencias (ligada pelo idTransferencia), confere tambem que envio e recebimento
 * aparecem como causalmente relacionados - e nunca como concorrentes.
 */
public final class MesclarLogs {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final List<String> LADO_ORIGEM = List.of("TRANSFERENCIA_DEBITO", "TRANSFERENCIA_ENVIADA");
    private static final List<String> RECEBIMENTOS = List.of("TRANSFERENCIA_CREDITO_REMOTO", "CREDITO_REMOTO_FALHOU");

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

        imprimirLinhaDoTempo(todosEventos);
        int concorrentes = imprimirConcorrentes(todosEventos);
        imprimirTransferencias(todosEventos);
        imprimirResumo(todosEventos, concorrentes);
    }

    private static void imprimirLinhaDoTempo(List<Evento> eventos) {
        System.out.println("=== Linha do tempo (ordenada por hora de parede) ===");
        for (Evento evento : eventos) {
            System.out.printf("[%s] vetor=%-9s %-28s %s%n",
                    evento.agencia(), vetor(evento), evento.tipo(), resumoDetalhes(evento));
        }
    }

    private static int imprimirConcorrentes(List<Evento> eventos) {
        System.out.println();
        System.out.println("=== Pares de eventos CONCORRENTES entre agencias diferentes ===");
        int encontrados = 0;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                Evento e1 = eventos.get(i);
                Evento e2 = eventos.get(j);
                if (e1.agencia().equals(e2.agencia())) {
                    continue;
                }
                if (relacao(e1, e2) == Relacao.CONCORRENTES) {
                    encontrados++;
                    System.out.printf("[%s] %-28s %-9s  x  [%s] %-28s %s%n",
                            e1.agencia(), e1.tipo(), vetor(e1), e2.agencia(), e2.tipo(), vetor(e2));
                }
            }
        }
        if (encontrados == 0) {
            System.out.println("(nenhum par concorrente encontrado nesta execucao - gere mais eventos em paralelo"
                    + " e rode de novo)");
        }
        return encontrados;
    }

    // Debito/envio na origem e credito no destino da MESMA transferencia: tem que dar ANTES.
    private static void imprimirTransferencias(List<Evento> eventos) {
        System.out.println();
        System.out.println("=== Transferencias entre agencias: envio x recebimento (devem ser causais) ===");
        boolean alguma = false;
        for (Evento envio : eventos) {
            if (!LADO_ORIGEM.contains(envio.tipo())) {
                continue;
            }
            Object id = envio.detalhes().get("idTransferencia");
            for (Evento recebimento : eventos) {
                if (RECEBIMENTOS.contains(recebimento.tipo())
                        && Objects.equals(id, recebimento.detalhes().get("idTransferencia"))) {
                    alguma = true;
                    Relacao r = relacao(envio, recebimento);
                    System.out.printf("%s  [%s] %s %s  ->  [%s] %s %s  : %s%s%n",
                            curto(id),
                            envio.agencia(), envio.tipo(), vetor(envio),
                            recebimento.agencia(), recebimento.tipo(), vetor(recebimento),
                            r, r == Relacao.ANTES ? " (aconteceu-antes, NAO concorrente)" : "  <-- inesperado");
                }
            }
        }
        if (!alguma) {
            System.out.println("(nenhuma transferencia entre agencias recebida ainda)");
        }
    }

    private static void imprimirResumo(List<Evento> eventos, int concorrentes) {
        int paresEntreAgencias = 0;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                if (!eventos.get(i).agencia().equals(eventos.get(j).agencia())) {
                    paresEntreAgencias++;
                }
            }
        }
        System.out.println();
        System.out.println("=== Resumo ===");
        System.out.println("Eventos no total ......................... " + eventos.size());
        System.out.println("Pares de agencias diferentes comparados .. " + paresEntreAgencias);
        System.out.println("  concorrentes (nenhum influenciou o outro) " + concorrentes);
        System.out.println("  causalmente relacionados ............... " + (paresEntreAgencias - concorrentes));
    }

    private static Relacao relacao(Evento e1, Evento e2) {
        return RelogioVetorial.comparar(e1.timestampVetorial(), e2.timestampVetorial());
    }

    private static String vetor(Evento evento) {
        return Arrays.toString(evento.timestampVetorial()).replace(" ", "");
    }

    private static String resumoDetalhes(Evento evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>(evento.detalhes());
        detalhes.computeIfPresent("idTransferencia", (chave, valor) -> curto(valor));
        return MAPPER.writeValueAsString(detalhes);
    }

    // So os 8 primeiros caracteres do UUID: o bastante para casar os eventos a olho.
    private static String curto(Object idTransferencia) {
        String texto = String.valueOf(idTransferencia);
        return texto.substring(0, Math.min(8, texto.length()));
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
