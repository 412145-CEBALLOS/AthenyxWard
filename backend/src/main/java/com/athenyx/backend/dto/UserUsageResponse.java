package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;

import java.time.LocalDateTime;

/**
 * Aggregated usage snapshot for the current user, returned by
 * {@code GET /api/auth/me/usage}.
 */
public record UserUsageResponse(
    UserInfo user,
    AnalysisUsage analysis,
    ReminderUsage reminders,
    EmailUsage emails,
    SessionUsage sessions,
    DataInventory dataInventory
) {

    public record AnalysisUsage(int used, Integer limit, LocalDateTime trialEndDate, boolean expired) {}

    public record ReminderUsage(long active, long done) {}

    public record EmailUsage(long total, long important, long hidden, long deleted) {}

    public record SessionUsage(int active) {}

    public record DataInventory(
        long emails,
        long analyses,
        long aiExplanations,
        long reminders,
        long auditEvents,
        LocalDateTime oldestRecordAt
    ) {}
}
