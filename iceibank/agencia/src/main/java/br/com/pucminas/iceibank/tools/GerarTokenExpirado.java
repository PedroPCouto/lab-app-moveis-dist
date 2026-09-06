package br.com.pucminas.iceibank.tools;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.model.TipoToken;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public final class GerarTokenExpirado {
    private GerarTokenExpirado() {
    }

    public static void main(String[] args) {
        String segredo = System.getenv().getOrDefault("JWT_SEGREDO", IceibankProperties.SEGREDO_PADRAO);
        String sujeito = args.length > 0 ? args[0] : "ana";

        SecretKey chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        Instant expirouEm = Instant.now().minus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .issuer("agencia-0")
                .subject(sujeito)
                .claim("nome", sujeito)
                .claim("tipo", TipoToken.CLIENTE.name())
                .issuedAt(Date.from(expirouEm.minus(30, ChronoUnit.MINUTES)))
                .expiration(Date.from(expirouEm))
                .signWith(chave)
                .compact();

        System.out.println(token);
    }
}
