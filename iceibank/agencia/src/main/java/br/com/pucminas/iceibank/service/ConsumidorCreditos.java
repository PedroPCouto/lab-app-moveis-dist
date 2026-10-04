package br.com.pucminas.iceibank.service;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.config.MensageriaConfig;
import br.com.pucminas.iceibank.dto.MensagemCredito;

/**
 * Lado "subscribe": consome a fila desta agencia. Nao passa pelo Spring MVC nem pelo
 * JwtFilter, logo nao ha cabecalho Authorization nem JWT aqui (Pergunta 3 da Parte C).
 *
 * <p>O ack e automatico ao final do metodo: se a agencia cair no meio do processamento,
 * o RabbitMQ entrega a mensagem de novo quando ela voltar. Excecao = reject sem requeue,
 * e a mensagem vai para a dead-letter queue (ver MensageriaConfig).
 */
@Component
public class ConsumidorCreditos {
    private final TransferenciaService transferenciaService;
    private final ObjectMapper mapper;

    public ConsumidorCreditos(TransferenciaService transferenciaService, ObjectMapper mapper) {
        this.transferenciaService = transferenciaService;
        this.mapper = mapper;
    }

    @RabbitListener(queues = MensageriaConfig.PREFIXO_FILA + "${iceibank.agencia-id}")
    public void aoReceber(Message mensagem) {
        MensagemCredito credito = mapper.readValue(mensagem.getBody(), MensagemCredito.class);
        if (!transferenciaService.creditarRemoto(credito)) {
            // Funcionalidade adicional: em vez de confirmar (ack) e perder o credito, rejeita
            // sem devolver a fila - o broker move a mensagem para a fila de mortas.
            throw new AmqpRejectAndDontRequeueException("Conta " + credito.idConta()
                    + " nao encontrada; transferencia " + credito.idTransferencia()
                    + " enviada para a fila de mensagens mortas.");
        }
    }
}
