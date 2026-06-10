package com.athenyx.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Thin wrapper around the {@code jjwt} library that issues and validates
 * access tokens. The token payload is fixed:
 * <ul>
 *     <li>{@code sub} — user email</li>
 *     <li>{@code userId} — primary key (used as the Spring Security
 *         principal)</li>
 *     <li>{@code role} — user role string</li>
 *     <li>{@code tokenVersion} — monotonic counter used to invalidate
 *         the token from the server side</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email, String role) {
        return generateToken(userId, email, role, 0L, expirationMs);
    }

    public String generateToken(Long userId, String email, String role, long tokenVersion) {
        return generateToken(userId, email, role, tokenVersion, expirationMs);
    }

    public String generateToken(Long userId, String email, String role, long tokenVersion, long ttlMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return validateToken(token).get("userId", Long.class);
    }

    public String getEmail(String token) {
        return validateToken(token).getSubject();
    }

    public String getRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    public Long getTokenVersion(String token) {
        Claims claims = validateToken(token);
        Object v = claims.get("tokenVersion");
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
