package br.com.pucminas.iceibank.service;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.config.MensageriaConfig;
import br.com.pucminas.iceibank.dto.MensagemCredito;

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
            throw new AmqpRejectAndDontRequeueException("Conta " + credito.idConta()
                    + " nao encontrada; transferencia " + credito.idTransferencia()
                    + " enviada para a fila de mensagens mortas.");
        }
    }
}
