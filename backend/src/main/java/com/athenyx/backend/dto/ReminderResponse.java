package com.athenyx.backend.dto;

import java.time.LocalDateTime;

/**
 * Full reminder view returned by the reminder CRUD endpoints.
 */
public record ReminderResponse(
    Long id,
    Long emailId,
    LocalDateTime reminderDate,
    String message,
    boolean done,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
