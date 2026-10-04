package br.com.pucminas.iceibank.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ (Parte A): uma exchange topic compartilhada e uma fila duravel
 * por agencia, ligada pela routing key {@code agencia.<id>.creditar}.
 *
 * <p>Toda agencia declara as filas das 3 agencias, nao so a propria. Declarar e
 * idempotente, e assim a fila de destino ja existe mesmo que aquela agencia nunca tenha
 * subido: uma exchange topic descarta em silencio a mensagem que nao casa com nenhuma
 * fila, e e exatamente o caso "agencia de destino fora do ar" que a mensageria deve cobrir.
 */
@Configuration
public class MensageriaConfig {
    public static final String EXCHANGE = "iceibank.eventos";

    public static final String PREFIXO_FILA = "fila-agencia-";

    public static String nomeFila(int idAgencia) {
        return PREFIXO_FILA + idAgencia;
    }

    public static String routingKeyCredito(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    @Bean
    Declarables topologiaIceibank() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);

        List<Declarable> declaracoes = new ArrayList<>();
        declaracoes.add(exchange);
        for (int id = 0; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            Queue fila = QueueBuilder.durable(nomeFila(id)).build();
            declaracoes.add(fila);
            declaracoes.add(BindingBuilder.bind(fila).to(exchange).with(routingKeyCredito(id)));
        }
        return new Declarables(declaracoes);
    }
}
