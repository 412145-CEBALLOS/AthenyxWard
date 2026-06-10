package com.athenyx.backend.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void isActive_isTrueWhenNotRevokedAndBothExpirationsInFuture() {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .revokedAt(null)
                .expiresAt(now.plusDays(1))
                .absoluteExpiresAt(now.plusDays(10))
                .build();

        assertThat(token.isActive(now)).isTrue();
    }

    @Test
    void isActive_isFalseWhenRevoked() {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .revokedAt(now.minusMinutes(1))
                .expiresAt(now.plusDays(1))
                .absoluteExpiresAt(now.plusDays(10))
                .build();

        assertThat(token.isActive(now)).isFalse();
    }

    @Test
    void isActive_isFalseWhenSlidingExpired() {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .expiresAt(now.minusSeconds(1))
                .absoluteExpiresAt(now.plusDays(10))
                .build();

        assertThat(token.isActive(now)).isFalse();
    }

    @Test
    void isActive_isFalseWhenAbsoluteExpired() {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = RefreshToken.builder()
                .expiresAt(now.plusDays(1))
                .absoluteExpiresAt(now.minusSeconds(1))
                .build();

        assertThat(token.isActive(now)).isFalse();
    }

    @Test
    void newFamilyId_isUuidString() {
        String id1 = RefreshToken.newFamilyId();
        String id2 = RefreshToken.newFamilyId();

        assertThat(id1).isNotBlank();
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1).hasSize(36);
    }
}
