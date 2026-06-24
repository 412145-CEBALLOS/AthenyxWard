package com.athenyx.backend.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/reminders}. The wrapper leaves
 * room to add pagination / counters later without breaking the SPA.
 */
public record RemindersListResponse(
    List<ReminderResponse> items
) {}
