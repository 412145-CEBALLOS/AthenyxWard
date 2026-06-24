package com.athenyx.backend.dto;

import java.time.LocalDateTime;

/**
 * Lightweight reminder payload used to enrich email list/detail
 * endpoints without forcing the SPA to make a second round-trip per
 * email. Mirrors the structure of {@link ReminderResponse} but drops
 * message, createdAt, updatedAt — the chip/banner UI only needs to
 * know the date and the done flag.
 */
public record ReminderSummary(
    Long id,
    LocalDateTime reminderDate,
    boolean done
) {}
