package com.athenyx.backend.dto;

import java.util.List;

public record AuditPageResponse(
    List<AuditEntryResponse> items,
    int currentPage,
    int totalPages,
    long totalItems
) {}
