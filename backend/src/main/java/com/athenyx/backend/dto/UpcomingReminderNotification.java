package com.athenyx.backend.dto;

import java.time.LocalDateTime;

/**
 * Notification entry returned by
 * {@code GET /api/notifications/upcoming}. Bundles the reminder
 * data with the email subject / sender so the SPA can render the
 * bell panel without a second round-trip per row.
 *
 * <p>{@code isOverdue} is computed server-side: a reminder whose
 * due time has already passed but is still within the
 * "upcoming" window (last 24 h). The frontend uses it to
 * decide whether to show a toast (overdue) or just a panel
 * entry (still upcoming).</p>
 */
public record UpcomingReminderNotification(
    Long reminderId,
    Long emailId,
    String emailSubject,
    String emailSender,
    String message,
    LocalDateTime reminderDate,
    boolean isOverdue
) {}
