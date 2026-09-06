package br.com.pucminas.iceibank.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.model.Evento;

@Component
public class RegistroEventos {
    private static final Logger log = LoggerFactory.getLogger(RegistroEventos.class);

    private static final int MAX_EM_MEMORIA = 2000;

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper mapper;
    private final Deque<Evento> emMemoria = new ConcurrentLinkedDeque<>();

    @Autowired
    public RegistroEventos(AgenciaConfig config, IceibankProperties propriedades, ObjectMapper mapper)
            throws IOException {
        this(config.getNomeAgencia(), Path.of(propriedades.pastaDados()), mapper);
    }

    public RegistroEventos(String nomeAgencia, Path pastaDados, ObjectMapper mapper) throws IOException {
        this.nomeAgencia = nomeAgencia;
        this.mapper = mapper;
        Files.createDirectories(pastaDados);
        this.caminhoArquivo = pastaDados.resolve("eventos-" + nomeAgencia + ".jsonl");
    }

    public Evento registrar(String tipo, int timestampLamport, Map<String, Object> detalhes) {
        Evento evento = new Evento(
                nomeAgencia,
                tipo,
                timestampLamport,
                Instant.now().toString(),
                new LinkedHashMap<>(detalhes));

        gravar(evento);
        guardarEmMemoria(evento);

        log.info("[Lamport {}] {} {}", timestampLamport, tipo, detalhes);
        return evento;
    }

    private synchronized void gravar(Evento evento) {
        try {
            String linha = mapper.writeValueAsString(evento);
            Files.writeString(caminhoArquivo, linha + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel gravar em " + caminhoArquivo, e);
        }
    }

    private void guardarEmMemoria(Evento evento) {
        emMemoria.addLast(evento);
        while (emMemoria.size() > MAX_EM_MEMORIA) {
            emMemoria.pollFirst();
        }
    }

    public List<Evento> eventos() {
        return new ArrayList<>(emMemoria);
    }

    public Path getCaminhoArquivo() {
        return caminhoArquivo;
    }
}
