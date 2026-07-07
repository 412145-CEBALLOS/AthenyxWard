package com.athenyx.backend.dto;

import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

/**
 * Full Gmail message view returned by {@code GET /api/emails/{id}}.
 *
 * <p>Marks the message as read on retrieval. Includes both the
 * analysis-ready plain text and the HTML preview (also truncated to
 * fit the column).</p>
 *
 * <p>{@code reminder} is non-null when the current user has a
 * reminder configured for this email — the SPA uses it to render
 * the banner inside the viewer without an extra round-trip.</p>
 */
public record EmailDetail(
    Long id,
    String gmailId,
    String sender,
    String senderName,
    String subject,
    String snippet,
    String contentForAnalysis,
    String htmlContent,
    LocalDateTime receivedAt,
    LocalDateTime fetchedAt,
    boolean isRead,
    String originalDateHeader,
    boolean isImportant,
    boolean isHidden,
    boolean isDeleted,
    @Nullable ReminderSummary reminder
) {}
