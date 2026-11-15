package br.com.pucminas.iceibank.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.config.BullyProperties;
import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;

/**
 * Implementacao real do {@link ClienteBully}: {@code java.net.http.HttpClient} (nativo do
 * Java, secao 11 do roteiro) com timeout tanto na conexao quanto na resposta inteira.
 * Equivale ao {@code axios.post(..., { timeout: TIMEOUT_MS })} do exemplo em Node.
 */
@Component
public class ClienteBullyHttp implements ClienteBully {
    private static final Logger log = LoggerFactory.getLogger(ClienteBullyHttp.class);

    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public ClienteBullyHttp(BullyProperties propriedades, ObjectMapper mapper) {
        this.timeout = propriedades.timeout();
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean enviarEleicao(String urlAgencia, MensagemEleicao mensagem) {
        return postar(urlAgencia + "/bully/eleicao", mensagem);
    }

    @Override
    public boolean enviarCoordenador(String urlAgencia, MensagemCoordenador mensagem) {
        return postar(urlAgencia + "/bully/coordenador", mensagem);
    }

    @Override
    public Optional<LiderResponse> consultarLider(String urlAgencia) {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(urlAgencia + "/bully/lider"))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(resposta.body(), LiderResponse.class));
        } catch (IOException | RuntimeException erro) {
            log.debug("GET {}/bully/lider falhou: {}", urlAgencia, erro.toString());
            return Optional.empty();
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private boolean postar(String url, Object corpo) {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(corpo)))
                .build();
        try {
            HttpResponse<Void> resposta = http.send(requisicao, HttpResponse.BodyHandlers.discarding());
            return resposta.statusCode() / 100 == 2;
        } catch (IOException erro) {
            // agencia fora do ar ou lenta demais - para o Bully, as duas coisas sao "nao respondeu"
            log.debug("POST {} falhou: {}", url, erro.toString());
            return false;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
