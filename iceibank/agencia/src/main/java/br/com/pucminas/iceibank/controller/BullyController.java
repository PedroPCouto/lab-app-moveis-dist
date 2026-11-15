package br.com.pucminas.iceibank.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;
import br.com.pucminas.iceibank.service.BullyService;
import jakarta.validation.Valid;

/**
 * Rotas do Bully (secao 5.2 do roteiro). Sao publicas - sem JWT - como no exemplo: quem
 * chama e outra agencia, nao uma pessoa usuaria. As consequencias disso estao discutidas
 * na Pergunta 3 da Parte A (RESPOSTAS.md).
 */
@RestController
@RequestMapping("/bully")
public class BullyController {
    private final BullyService bully;

    public BullyController(BullyService bully) {
        this.bully = bully;
    }

    // O 200 e o "OK" do algoritmo: "estou viva e vou conduzir a eleicao daqui em diante".
    @PostMapping("/eleicao")
    public Map<String, Object> eleicao(@Valid @RequestBody MensagemEleicao mensagem) {
        bully.receberEleicao(mensagem.origemId(), mensagem.vetorEnvio());
        return Map.of("ok", true, "idAgencia", bully.obterLiderAtual().idAgencia());
    }

    @PostMapping("/coordenador")
    public Map<String, Object> coordenador(@Valid @RequestBody MensagemCoordenador mensagem) {
        bully.receberCoordenador(mensagem.liderId(), mensagem.vetorEnvio());
        return Map.of("ok", true);
    }

    @GetMapping("/lider")
    public LiderResponse lider() {
        return bully.obterLiderAtual();
    }
}
