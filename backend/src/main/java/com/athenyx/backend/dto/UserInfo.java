package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;

import java.time.LocalDateTime;

/**
 * Public profile exposed by {@code GET /api/auth/me} and friends. The
 * record intentionally omits security-sensitive fields (token hashes,
 * encrypted Google credentials, {@code tokenVersion}).
 */
public record UserInfo(
    Long id,
    String name,
    String email,
    String pictureUrl,
    Role role,
    LocalDateTime trialEndDate,
    boolean trialExpired,
    boolean accessibilityMode,
    LocalDateTime termsAcceptedAt,
    String termsVersion,
    LocalDateTime lastLoginAt,
    Boolean emailVerified
) {}
