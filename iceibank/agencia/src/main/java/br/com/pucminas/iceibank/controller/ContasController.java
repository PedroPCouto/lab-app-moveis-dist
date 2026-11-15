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
import br.com.pucminas.iceibank.dto.ContaComVetor;
import br.com.pucminas.iceibank.dto.CriarContaRequest;
import br.com.pucminas.iceibank.dto.EventoResponse;
import br.com.pucminas.iceibank.dto.SincronizacaoRequest;
import br.com.pucminas.iceibank.dto.SincronizacaoResponse;
import br.com.pucminas.iceibank.dto.ValorRequest;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.Conta;
import br.com.pucminas.iceibank.model.Evento;
import br.com.pucminas.iceibank.service.ContaService;
import br.com.pucminas.iceibank.service.SincronizacaoService;
import jakarta.validation.Valid;

/**
 * Sprint 3: as leituras e escritas de conta passam a devolver tambem o {@code vetorAtual}
 * (secao 9.3) - e o que o app guarda para operar offline. Os campos antigos continuam
 * iguais, entao o frontend web do Sprint 1 nao percebe a diferenca.
 */
@RestController
@RequestMapping("/contas")
public class ContasController {
    private final ContaService contaService;
    private final SincronizacaoService sincronizacaoService;
    private final AgenciaConfig config;

    public ContasController(ContaService contaService, SincronizacaoService sincronizacaoService,
                            AgenciaConfig config) {
        this.contaService = contaService;
        this.sincronizacaoService = sincronizacaoService;
        this.config = config;
    }

    @PostMapping
    public ResponseEntity<ContaComVetor> criarConta(@Valid @RequestBody CriarContaRequest requisicao,
                                                    @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        Conta conta = contaService.criar(requisicao.id(), requisicao.nomeAluno(), requisicao.saldoInicial(), auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(contaService.comVetor(conta));
    }

    @GetMapping
    public List<ContaComVetor> minhasContas(@RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.contasDoUsuarioComVetor(auth);
    }

    @GetMapping("/{id}")
    public ContaComVetor consultarSaldo(@PathVariable int id,
                                       @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.comVetor(contaService.buscarDoUsuario(id, auth));
    }

    @PostMapping("/{id}/depositar")
    public ContaComVetor depositar(@PathVariable int id, @Valid @RequestBody ValorRequest requisicao,
                                   @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.comVetor(contaService.depositar(id, requisicao.valor(), auth));
    }

    @PostMapping("/{id}/sacar")
    public ContaComVetor sacar(@PathVariable int id, @Valid @RequestBody ValorRequest requisicao,
                               @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return contaService.comVetor(contaService.sacar(id, requisicao.valor(), auth));
    }

    // Sprint 3, Parte E (secao 9.4): operacao feita offline pelo app; 409 se o vetor mudou.
    @PostMapping("/{id}/sincronizar")
    public SincronizacaoResponse sincronizar(@PathVariable int id, @Valid @RequestBody SincronizacaoRequest requisicao,
                                             @RequestAttribute(Autenticacao.ATRIBUTO) Autenticacao auth) {
        return sincronizacaoService.sincronizar(id, requisicao, auth);
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
