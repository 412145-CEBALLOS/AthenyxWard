package com.athenyx.backend.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";

    private JwtUtil jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil(SECRET, 60_000L);
    }

    @Test
    void generateToken_roundTripsUserIdEmailAndRole() {
        String token = jwt.generateToken(42L, "user@example.com", "PREMIUM");

        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.getUserId(token)).isEqualTo(42L);
        assertThat(jwt.getEmail(token)).isEqualTo("user@example.com");
        assertThat(jwt.getRole(token)).isEqualTo("PREMIUM");
    }

    @Test
    void generateToken_usesProvidedTokenVersion() {
        String token = jwt.generateToken(1L, "a@b.com", "TRIAL", 7L);

        assertThat(jwt.getTokenVersion(token)).isEqualTo(7L);
    }

    @Test
    void generateToken_usesCustomTtl() {
        long ttl = 5_000L;
        String token = jwt.generateToken(1L, "a@b.com", "TRIAL", 0L, ttl);

        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.getExpirationMs()).isEqualTo(60_000L);
    }

    @Test
    void getTokenVersion_defaultsToZero_whenClaimIsMissing() {
        String token = jwt.generateToken(1L, "a@b.com", "TRIAL");

        assertThat(jwt.getTokenVersion(token)).isEqualTo(0L);
    }

    @Test
    void isTokenValid_returnsFalse_onTamperedSignature() {
        String token = jwt.generateToken(1L, "a@b.com", "TRIAL");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwt.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_onGarbage() {
        assertThat(jwt.isTokenValid("not.a.jwt")).isFalse();
        assertThat(jwt.isTokenValid("")).isFalse();
    }

    @Test
    void getUserId_throws_onInvalidToken() {
        assertThatThrownBy(() -> jwt.getUserId("nope"))
                .isInstanceOf(Exception.class);
    }
}
