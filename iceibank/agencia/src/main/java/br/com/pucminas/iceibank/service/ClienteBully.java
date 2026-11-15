package br.com.pucminas.iceibank.service;

import java.util.Optional;

import br.com.pucminas.iceibank.dto.LiderResponse;
import br.com.pucminas.iceibank.dto.MensagemCoordenador;
import br.com.pucminas.iceibank.dto.MensagemEleicao;

/**
 * Como uma agencia fala com as outras durante a eleicao. Separado do {@link BullyService}
 * para que a logica de decisao (quem vence, quando reeleger) seja testada sem rede: os
 * testes trocam esta interface por uma "rede" em memoria onde agencias podem cair.
 *
 * <p>Toda chamada respeita o timeout configurado e nunca lanca excecao por falha de rede:
 * agencia fora do ar, conexao recusada e resposta lenta demais significam igualmente
 * "nao respondeu" - e e exatamente essa ambiguidade que a Pergunta 3 da Parte B explora.
 */
public interface ClienteBully {
    /** {@code true} se a agencia respondeu 2xx dentro do timeout (o "OK" do Bully). */
    boolean enviarEleicao(String urlAgencia, MensagemEleicao mensagem);

    /** {@code true} se a agencia confirmou o recebimento do anuncio. */
    boolean enviarCoordenador(String urlAgencia, MensagemCoordenador mensagem);

    /** Vazio se a agencia nao respondeu dentro do timeout. */
    Optional<LiderResponse> consultarLider(String urlAgencia);
}
