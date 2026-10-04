package br.com.pucminas.iceibank.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.dto.CreditoRemotoRequest;
import br.com.pucminas.iceibank.dto.TransferenciaRequest;
import br.com.pucminas.iceibank.dto.TransferenciaResponse;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;
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

    @PostMapping("/contas/{id}/creditar-remoto")
    public Map<String, Object> creditarRemoto(@PathVariable int id,
                                              @Valid @RequestBody CreditoRemotoRequest requisicao,
                                              @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        Conta conta = transferenciaService.creditarRemoto(
                id, requisicao.valor(), requisicao.vetorEnvio(), requisicao.origemAgencia());
        return Map.of(
                "mensagem", "Credito remoto aplicado.",
                "saldoAtual", conta.getSaldo(),
                "chamadoPor", auth.sujeito());
    }
}
