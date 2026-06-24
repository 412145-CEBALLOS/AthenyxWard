package com.athenyx.backend.service.reminder;

/**
 * Thrown when a reminder is not found, or the calling user does not
 * own it. Mapped to HTTP 404 — both cases are intentionally
 * indistinguishable from the SPA's perspective to avoid leaking
 * whether an id exists.
 */
public class ReminderNotFoundException extends RuntimeException {
    public ReminderNotFoundException(String message) {
        super(message);
    }
}
