package br.com.pucminas.iceibank.dto;

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
