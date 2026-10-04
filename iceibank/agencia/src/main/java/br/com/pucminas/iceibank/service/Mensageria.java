package br.com.pucminas.iceibank.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.MensageriaConfig;

@Service
public class Mensageria {
    private static final Duration ESPERA_CONFIRMACAO = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin admin;
    private final ObjectMapper mapper;

    public Mensageria(RabbitTemplate rabbitTemplate, AmqpAdmin admin, ObjectMapper mapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.admin = admin;
        this.mapper = mapper;
    }

    /**
     * Publica na exchange e so retorna depois que o broker confirmar (publisher confirm)
     * que aceitou a mensagem - para fila duravel e mensagem persistente, isso significa
     * gravada em disco. Sem a confirmacao, um "200 OK" na transferencia nao garantiria
     * nem que a mensagem chegou ao RabbitMQ. Qualquer falha sobe como AmqpException.
     */
    public void publicar(String routingKey, Object conteudo, String idMensagem) {
        Message mensagem = MessageBuilder.withBody(mapper.writeValueAsBytes(conteudo))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(idMensagem)
                .build();

        rabbitTemplate.invoke(operacoes -> {
            operacoes.send(MensageriaConfig.EXCHANGE, routingKey, mensagem);
            operacoes.waitForConfirmsOrDie(ESPERA_CONFIRMACAO.toMillis());
            return null;
        });
    }

    /**
     * Mensagens prontas para entrega em cada fila, segundo o broker. Com a agencia de
     * destino fora do ar, e aqui que se ve a mensagem esperando por ela.
     */
    public Map<String, Long> mensagensPorFila() {
        Map<String, Long> filas = new LinkedHashMap<>();
        for (int id = 0; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            String fila = MensageriaConfig.nomeFila(id);
            filas.put(fila, mensagensProntas(fila));
        }
        return filas;
    }

    private Long mensagensProntas(String fila) {
        try {
            QueueInformation info = admin.getQueueInfo(fila);
            return info == null ? null : info.getMessageCount();
        } catch (AmqpException brokerIndisponivel) {
            return null;
        }
    }
}
