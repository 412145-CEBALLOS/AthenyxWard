package com.athenyx.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request body for {@code POST /api/reminders}.
 *
 * <p>{@code message} is optional; the UI defaults to the email subject
 * when the field is blank.</p>
 */
public record CreateReminderRequest(
    @NotNull Long emailId,
    @NotNull LocalDateTime reminderDate,
    @Size(max = 500) String message
) {}
