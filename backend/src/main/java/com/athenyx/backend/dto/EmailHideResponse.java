package com.athenyx.backend.dto;

/**
 * Response body for {@code POST /api/emails/{id}/hide} and
 * {@code POST /api/emails/{id}/unhide}.
 * Contains the email id and its new hidden state.
 */
public record EmailHideResponse(
    Long emailId,
    boolean isHidden
) {}
