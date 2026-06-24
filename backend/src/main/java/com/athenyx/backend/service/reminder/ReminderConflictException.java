package com.athenyx.backend.service.reminder;

/**
 * Thrown when a user tries to create a second reminder for an email
 * that already has one. Mapped to HTTP 409 Conflict; the SPA reacts
 * by opening the existing reminder in the edit dialog.
 */
public class ReminderConflictException extends RuntimeException {
    public ReminderConflictException(String message) {
        super(message);
    }
}
