package com.athenyx.backend.dto;

import java.util.List;

public record PaymentHistoryResponse(
    List<PaymentResponse> items,
    int currentPage,
    int totalPages,
    long totalItems
) {}
