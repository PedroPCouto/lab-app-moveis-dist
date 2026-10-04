package br.com.pucminas.iceibank.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.dto.StatusResponse;
import br.com.pucminas.iceibank.service.ContaService;
import br.com.pucminas.iceibank.service.RegistroEventos;
import br.com.pucminas.iceibank.service.RelogioVetorial;

@RestController
public class StatusController {
    private final AgenciaConfig config;
    private final RelogioVetorial relogio;
    private final ContaService contaService;
    private final RegistroEventos registro;

    public StatusController(AgenciaConfig config, RelogioVetorial relogio, ContaService contaService,
                            RegistroEventos registro) {
        this.config = config;
        this.relogio = relogio;
        this.contaService = contaService;
        this.registro = registro;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(
                "no ar",
                config.getIdAgencia(),
                config.getPorta(),
                AgenciaConfig.NUMERO_AGENCIAS,
                relogio.valorAtual(),
                contaService.quantidade(),
                contaService.idsDasContas(),
                registro.eventos().size(),
                Instant.now().toString());
    }
}
