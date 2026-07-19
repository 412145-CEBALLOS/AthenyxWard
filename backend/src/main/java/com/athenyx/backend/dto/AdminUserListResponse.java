package com.athenyx.backend.dto;

import java.util.List;

public record AdminUserListResponse(
    List<AdminUserResponse> items,
    int currentPage,
    int totalPages,
    long totalItems
) {}
