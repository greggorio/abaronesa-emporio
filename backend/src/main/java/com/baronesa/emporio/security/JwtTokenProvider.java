package com.baronesa.emporio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Component
public class JwtTokenProvider {

    @Value("${integration.system-token-secret}")
    private String secretProperty;

    private SecretKey signingKey;

    private final int jwtExpirationInMs = 7 * 24 * 60 * 60 * 1000;

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(secretProperty.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subject, Set<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", roles)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtExpirationInMs))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateToken(String subject, Set<String> roles, Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", roles)
                .claim("userId", userId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtExpirationInMs))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateToken(Authentication authentication) {
        return generateToken(authentication.getName(), Set.of());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            System.out.println("JwtTokenProvider.validateToken - Token inválido: "
                    + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return false;
        }
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getBody();
    }
}
