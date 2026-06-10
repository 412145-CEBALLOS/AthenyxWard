package com.athenyx.backend.dto;

import java.util.List;

/**
 * Paginated response of {@code GET /api/emails/fetch}. Page size is
 * server-side (currently 20 messages).
 */
public record EmailPageResponse(
    List<EmailSummary> emails,
    int currentPage,
    int pageSize,
    boolean hasNextPage
) {}