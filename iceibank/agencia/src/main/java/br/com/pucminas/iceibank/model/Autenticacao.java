package br.com.pucminas.iceibank.model;

public record Autenticacao(String sujeito, String nome, TipoToken tipo) {
    public static final String ATRIBUTO = "iceibank.autenticacao";

    public boolean ehCliente() {
        return tipo == TipoToken.CLIENTE;
    }

    public boolean ehSistema() {
        return tipo == TipoToken.SISTEMA;
    }
}
