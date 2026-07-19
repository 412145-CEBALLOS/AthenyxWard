package com.athenyx.backend.service.reminder;

/**
 * Thrown when a user tries to create a reminder but has reached
 * the configured maximum per user. Mapped to HTTP 409 Conflict.
 */
public class ReminderQuotaExceededException extends RuntimeException {
    public ReminderQuotaExceededException(String message) {
        super(message);
    }
}
