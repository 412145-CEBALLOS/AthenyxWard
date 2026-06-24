package com.athenyx.backend.service.reminder;

/**
 * Thrown when a TRIAL user attempts to create a reminder — the
 * action is gated to PREMIUM/ADMIN by both the controller's
 * {@code @PreAuthorize} and the service as a safety net. Mapped to
 * HTTP 403 with the Spanish message that the SPA surfaces as a
 * "Mejora tu plan" upsell.
 */
public class ReminderPremiumRequiredException extends RuntimeException {
    public ReminderPremiumRequiredException(String message) {
        super(message);
    }
}
