package com.athenyx.backend.dto;

/**
 * Response body for {@code POST /api/emails/{id}/delete}.
 * Contains the email id and its new deleted state.
 */
public record EmailDeleteResponse(
    Long emailId,
    boolean isDeleted
) {}
