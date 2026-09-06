package br.com.pucminas.iceibank.dto;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEmSegundos,
        String usuario,
        String nome,
        int agencia) {
}
