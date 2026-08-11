package com.corebank.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final Duration DURACION_SESION = Duration.ofHours(2);

    @Value("${JWT_SECRET:}")
    private String secretoConfigurado;

    private SecretKey clave;

    @PostConstruct
    void init() {
        String secreto = secretoConfigurado;
        if (secreto == null || secreto.isBlank()) {
            byte[] buf = new byte[32];
            new SecureRandom().nextBytes(buf);
            secreto = HexFormat.of().formatHex(buf);
            log.warn("JWT_SECRET no está seteado — usando uno aleatorio generado al arrancar.");
            log.warn("Todas las sesiones se invalidan en cada reinicio. Definí JWT_SECRET en .env para producción.");
        }

        byte[] bytes = secreto.getBytes();
        if (bytes.length < 32) {
            bytes = java.util.Arrays.copyOf(bytes, 32);
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
    }

    public String generar(String usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(DURACION_SESION)))
                .signWith(clave)
                .compact();
    }

    public Optional<String> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String usuario = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return usuario == null || usuario.isBlank() ? Optional.empty() : Optional.of(usuario);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
