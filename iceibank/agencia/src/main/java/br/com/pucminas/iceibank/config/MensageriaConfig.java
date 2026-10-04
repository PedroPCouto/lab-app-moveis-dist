package br.com.pucminas.iceibank.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
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
 *
 * <p>Funcionalidade adicional (dead-letter): cada fila aponta para a exchange
 * {@code iceibank.mensagens-mortas}. Mensagem rejeitada pelo consumidor (conta nao
 * encontrada, corpo invalido) nao e descartada: o broker a move para
 * {@code fila-agencia-<id>.mortas}, de onde pode ser reprocessada depois.
 */
@Configuration
public class MensageriaConfig {
    public static final String EXCHANGE = "iceibank.eventos";

    public static final String PREFIXO_FILA = "fila-agencia-";

    public static final String EXCHANGE_MORTAS = "iceibank.mensagens-mortas";

    public static String nomeFila(int idAgencia) {
        return PREFIXO_FILA + idAgencia;
    }

    public static String nomeFilaMortas(int idAgencia) {
        return nomeFila(idAgencia) + ".mortas";
    }

    public static String routingKeyCredito(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    @Bean
    Declarables topologiaIceibank() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange exchangeMortas = new DirectExchange(EXCHANGE_MORTAS, true, false);

        List<Declarable> declaracoes = new ArrayList<>();
        declaracoes.add(exchange);
        declaracoes.add(exchangeMortas);
        for (int id = 0; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            // A mensagem morta mantem a routing key original (agencia.<id>.creditar), e e por
            // ela que a exchange de mortas a separa na fila de mortas da agencia certa.
            Queue fila = QueueBuilder.durable(nomeFila(id)).deadLetterExchange(EXCHANGE_MORTAS).build();
            Queue filaMortas = QueueBuilder.durable(nomeFilaMortas(id)).build();
            declaracoes.add(fila);
            declaracoes.add(filaMortas);
            declaracoes.add(BindingBuilder.bind(fila).to(exchange).with(routingKeyCredito(id)));
            declaracoes.add(BindingBuilder.bind(filaMortas).to(exchangeMortas).with(routingKeyCredito(id)));
        }
        return new Declarables(declaracoes);
    }
}
