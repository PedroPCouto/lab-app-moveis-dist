package br.com.pucminas.iceibank.service;

import java.util.Optional;

import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;

public interface ClienteBully {
    boolean enviarEleicao(String urlAgencia, MensagemEleicao mensagem);

    boolean enviarCoordenador(String urlAgencia, MensagemCoordenador mensagem);

    Optional<LiderResponse> consultarLider(String urlAgencia);
}
