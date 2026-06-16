package com.athenyx.backend.dto;

import java.time.LocalDateTime;

/**
 * Lightweight Gmail message used in list endpoints. Excludes the full
 * body and the HTML preview — the SPA fetches those lazily through
 * {@code GET /api/emails/{id}}.
 */
public record EmailSummary(
    Long id,
    String gmailId,
    String sender,
    String senderName,
    String subject,
    String snippet,
    LocalDateTime receivedAt,
    LocalDateTime fetchedAt,
    boolean isRead,
    String originalDateHeader,
    boolean isImportant
) {}
