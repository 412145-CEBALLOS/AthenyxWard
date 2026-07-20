package com.athenyx.backend.dto;

import java.time.LocalDateTime;

/**
 * Active session (refresh-token family) for the current user,
 * returned by {@code GET /api/auth/me/sessions}.
 */
public record ActiveSessionResponse(
    Long id,
    String familyId,
    String userAgent,
    String ip,
    LocalDateTime issuedAt,
    LocalDateTime lastUsedAt,
    boolean current
) {}
