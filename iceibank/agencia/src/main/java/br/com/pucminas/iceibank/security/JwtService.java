package br.com.pucminas.iceibank.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.Autenticacao;
import br.com.pucminas.iceibank.model.TipoToken;
import br.com.pucminas.iceibank.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private static final int TAMANHO_MINIMO_CHAVE = 32;

    private static final String CLAIM_NOME = "nome";
    private static final String CLAIM_TIPO = "tipo";

    private final SecretKey chave;
    private final Duration expiracao;
    private final String emissor;

    public JwtService(IceibankProperties propriedades, AgenciaConfig agenciaConfig) {
        byte[] segredo = propriedades.jwt().segredo().getBytes(StandardCharsets.UTF_8);
        if (segredo.length < TAMANHO_MINIMO_CHAVE) {
            throw new IllegalStateException(
                    "iceibank.jwt.segredo precisa ter ao menos " + TAMANHO_MINIMO_CHAVE + " bytes para HS256.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo);
        this.expiracao = propriedades.jwt().expiracao();
        this.emissor = agenciaConfig.getNomeAgencia();
    }

    public String gerarTokenCliente(Usuario usuario) {
        return gerar(usuario.usuario(), usuario.nome(), TipoToken.CLIENTE, expiracao);
    }

    public String gerarTokenExpirado(String sujeito) {
        return gerar(sujeito, sujeito, TipoToken.CLIENTE, Duration.ofSeconds(-60));
    }

    private String gerar(String sujeito, String nome, TipoToken tipo, Duration validade) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .issuer(emissor)
                .subject(sujeito)
                .claim(CLAIM_NOME, nome)
                .claim(CLAIM_TIPO, tipo.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }

    public long segundosDeValidade() {
        return expiracao.toSeconds();
    }

    public Autenticacao validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            TipoToken tipo = TipoToken.valueOf(claims.get(CLAIM_TIPO, String.class));
            return new Autenticacao(claims.getSubject(), claims.get(CLAIM_NOME, String.class), tipo);
        } catch (ExpiredJwtException e) {
            throw ApiException.naoAutenticado("Token expirado. Faca login novamente.");
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.naoAutenticado("Token invalido.");
        }
    }
}
