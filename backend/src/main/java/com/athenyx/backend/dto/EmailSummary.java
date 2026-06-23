package com.athenyx.backend.dto;

import com.athenyx.backend.heuristics.ThreatLevel;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

/**
 * Lightweight Gmail message used in list endpoints. Excludes the full
 * body and the HTML preview — the SPA fetches those lazily through
 * {@code GET /api/emails/{id}}.
 *
 * <p>{@code riskPercentage} and {@code riskLevel} carry the result of the
 * most recent security analysis (if any). Both are {@code null} when the
 * email has never been analysed. When present, the SPA can render the
 * traffic-light indicator without a second round-trip.</p>
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
    boolean isImportant,
    @Nullable Integer riskPercentage,
    @Nullable ThreatLevel riskLevel
) {}
