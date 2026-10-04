package br.com.pucminas.iceibank.controller;

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

    // A rota POST /contas/{id}/creditar-remoto do Sprint 1 deixou de existir: o credito
    // remoto agora chega pela fila do RabbitMQ (ConsumidorCreditos).
}
