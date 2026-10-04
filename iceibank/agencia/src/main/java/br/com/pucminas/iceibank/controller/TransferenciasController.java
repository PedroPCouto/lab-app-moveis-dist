package br.com.pucminas.iceibank.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.dto.TransferenciaRequest;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.service.TransferenciaService;
import jakarta.validation.Valid;

@RestController
public class TransferenciasController {
    private final TransferenciaService transferenciaService;

    public TransferenciasController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping("/transferencias")
    public TransferenciaResponse transferir(@Valid @RequestBody TransferenciaRequest requisicao,
                                            @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return transferenciaService.transferir(
                requisicao.idOrigem(), requisicao.idDestino(), requisicao.valor(), auth);
    }

    // Funcionalidade adicional: devolve os creditos que morreram nesta agencia (conta nao
    // encontrada) para a fila, depois que o problema foi resolvido (ex.: conta recriada).
    @PostMapping("/mensagens-mortas/reprocessar")
    public Map<String, Object> reprocessarMensagensMortas(
            @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        int reprocessadas = transferenciaService.reprocessarCreditosMortos();
        return Map.of(
                "mensagem", "Mensagens mortas devolvidas para a fila desta agencia.",
                "reprocessadas", reprocessadas,
                "solicitadoPor", auth.sujeito());
    }

    // A rota POST /contas/{id}/creditar-remoto do Sprint 1 deixou de existir: o credito
    // remoto agora chega pela fila do RabbitMQ (ConsumidorCreditos).
}
