package br.com.pucminas.iceibank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.dto.LoginRequest;
import br.com.pucminas.iceibank.dto.LoginResponse;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.security.JwtService;
import br.com.pucminas.iceibank.security.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final AgenciaConfig config;

    public AuthController(UsuarioService usuarioService, JwtService jwtService, AgenciaConfig config) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.config = config;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest requisicao) {
        var usuario = usuarioService.autenticar(requisicao.usuario(), requisicao.senha())
                .orElseThrow(() -> ApiException.naoAutenticado("Usuario ou senha invalidos."));

        return ResponseEntity.ok(new LoginResponse(
                jwtService.gerarTokenCliente(usuario),
                "Bearer",
                jwtService.segundosDeValidade(),
                usuario.usuario(),
                usuario.nome(),
                config.getIdAgencia()));
    }
}
