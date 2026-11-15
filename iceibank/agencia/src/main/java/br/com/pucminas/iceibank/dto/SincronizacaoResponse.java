package br.com.pucminas.iceibank.dto;

/**
 * Resposta de uma sincronizacao aceita. {@code conta.vetorAtual} e o vetor logo depois de a
 * operacao ser aplicada: o app passa a usa-lo como novo "vetor conhecido" (inclusive para a
 * proxima operacao da fila, feita em cima desta). {@code duplicada = true} quando o mesmo
 * {@code idOperacao} ja tinha sido aplicado - o app reenviou porque nao recebeu a resposta.
 */
public record SincronizacaoResponse(
        String mensagem,
        String idOperacao,
        String tipo,
        boolean duplicada,
        ContaComVetor conta,
        TransferenciaResponse transferencia) {

    public SincronizacaoResponse comoDuplicada() {
        return new SincronizacaoResponse("Operacao ja havia sido sincronizada; nada foi aplicado de novo.",
                idOperacao, tipo, true, conta, transferencia);
    }
}
