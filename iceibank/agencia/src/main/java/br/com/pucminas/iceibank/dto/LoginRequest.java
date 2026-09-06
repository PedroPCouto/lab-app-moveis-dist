package br.com.pucminas.iceibank.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "e obrigatorio") String usuario,
        @NotBlank(message = "e obrigatoria") String senha) {
}
