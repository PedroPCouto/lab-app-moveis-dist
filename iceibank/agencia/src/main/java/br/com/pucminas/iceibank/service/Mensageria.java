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

import com.rabbitmq.client.GetResponse;

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

    public Map<String, Long> mensagensPorFila() {
        Map<String, Long> filas = new LinkedHashMap<>();
        for (int id = 0; id < AgenciaConfig.NUMERO_AGENCIAS; id++) {
            String fila = MensageriaConfig.nomeFila(id);
            String filaMortas = MensageriaConfig.nomeFilaMortas(id);
            filas.put(fila, mensagensProntas(fila));
            filas.put(filaMortas, mensagensProntas(filaMortas));
        }
        return filas;
    }

    public int reprocessarMortas(int idAgencia) {
        String filaMortas = MensageriaConfig.nomeFilaMortas(idAgencia);
        String routingKey = MensageriaConfig.routingKeyCredito(idAgencia);
        Long pendentes = mensagensProntas(filaMortas);
        if (pendentes == null || pendentes == 0) {
            return 0;
        }

        Integer movidas = rabbitTemplate.execute(canal -> {
            canal.confirmSelect();
            int total = 0;
            for (long i = 0; i < pendentes; i++) {
                GetResponse morta = canal.basicGet(filaMortas, false);
                if (morta == null) {
                    break;
                }
                canal.basicPublish(MensageriaConfig.EXCHANGE, routingKey, morta.getProps(), morta.getBody());
                canal.waitForConfirmsOrDie(ESPERA_CONFIRMACAO.toMillis());
                canal.basicAck(morta.getEnvelope().getDeliveryTag(), false);
                total++;
            }
            return total;
        });
        return movidas == null ? 0 : movidas;
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
