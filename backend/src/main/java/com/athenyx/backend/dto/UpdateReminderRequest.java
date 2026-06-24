package com.athenyx.backend.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request body for {@code PATCH /api/reminders/{id}}. Every field is
 * optional — only the non-null members are applied. Use a sentinel
 * pattern (sending {@code null}) to leave a field untouched.
 */
public record UpdateReminderRequest(
    LocalDateTime reminderDate,
    @Size(max = 500) String message,
    Boolean done
) {}
