package br.com.pucminas.iceibank.service;

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
 * o RabbitMQ entrega a mensagem de novo quando ela voltar.
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
        transferenciaService.creditarRemoto(credito);
    }
}
