package com.athenyx.backend.dto;

/**
 * Response body for {@code POST /api/emails/{id}/important}.
 * Contains the toggled email id and its new important state.
 */
public record EmailImportantToggleResponse(
    Long emailId,
    boolean isImportant
) {}
