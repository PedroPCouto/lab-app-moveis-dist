package br.com.pucminas.iceibank.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final String PREFIXO = "Bearer ";

    private static final List<String> ROTAS_PUBLICAS = List.of("/auth/login", "/status", "/error");

    private final JwtService jwtService;
    private final ObjectMapper mapper;

    public JwtFilter(JwtService jwtService, ObjectMapper mapper) {
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String caminho = request.getRequestURI();
        return ROTAS_PUBLICAS.stream().anyMatch(caminho::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Autenticacao autenticacao = jwtService.validar(extrairToken(request));
            verificarTipoPermitido(autenticacao);
            request.setAttribute(Autenticacao.ATRIBUTO, autenticacao);
        } catch (ApiException e) {
            responder(response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    private String extrairToken(HttpServletRequest request) {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            throw ApiException.naoAutenticado(
                    "Token ausente. Envie o cabecalho Authorization: Bearer <token>.");
        }
        return cabecalho.substring(PREFIXO.length()).trim();
    }

    // Sprint 2: a rota interna /contas/{id}/creditar-remoto deixou de existir (o credito
    // remoto chega pelo RabbitMQ), entao toda rota protegida e de cliente.
    private void verificarTipoPermitido(Autenticacao autenticacao) {
        if (!autenticacao.ehCliente()) {
            throw ApiException.proibido("Somente tokens de cliente podem operar contas.");
        }
    }

    private void responder(HttpServletResponse response, ApiException erro) throws IOException {
        HttpStatus status = erro.getStatus();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("erro", erro.getMessage()));
    }
}
