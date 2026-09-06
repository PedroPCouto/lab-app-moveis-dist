package br.com.pucminas.iceibank.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.dto.CriarContaRequest;
import br.com.pucminas.iceibank.dto.EventoResponse;
import br.com.pucminas.iceibank.dto.ValorRequest;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;
import br.com.pucminas.iceibank.model.Evento;
import br.com.pucminas.iceibank.service.ContaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/contas")
public class ContasController {
    private final ContaService contaService;
    private final AgenciaConfig config;

    public ContasController(ContaService contaService, AgenciaConfig config) {
        this.contaService = contaService;
        this.config = config;
    }

    @PostMapping
    public ResponseEntity<Conta> criarConta(@Valid @RequestBody CriarContaRequest requisicao,
                                            @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        Conta conta = contaService.criar(requisicao.id(), requisicao.nomeAluno(), requisicao.saldoInicial(), auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(conta);
    }

    @GetMapping
    public List<Conta> minhasContas(@RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.contasDoUsuario(auth);
    }

    @GetMapping("/{id}")
    public Conta consultarSaldo(@PathVariable int id,
                                @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.buscarDoUsuario(id, auth);
    }

    @PostMapping("/{id}/depositar")
    public Conta depositar(@PathVariable int id, @Valid @RequestBody ValorRequest requisicao,
                           @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.depositar(id, requisicao.valor(), auth);
    }

    @PostMapping("/{id}/sacar")
    public Conta sacar(@PathVariable int id, @Valid @RequestBody ValorRequest requisicao,
                       @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.sacar(id, requisicao.valor(), auth);
    }

    @GetMapping("/{id}/historico")
    public EventoResponse historico(@PathVariable int id,
                                    @RequestParam(defaultValue = "20") int limite,
                                    @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        contaService.buscarDoUsuario(id, auth);
        List<Evento> eventos = contaService.historico(id, Math.clamp(limite, 1, 200));
        return new EventoResponse(id, config.getNomeAgencia(), eventos.size(), eventos);
    }
}
