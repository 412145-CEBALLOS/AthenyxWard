package com.athenyx.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisHistoryResponse(
    List<AnalysisHistoryItem> items,
    int currentPage,
    int totalPages,
    long totalItems
) {
    public record AnalysisHistoryItem(
        Long analysisId,
        Long emailId,
        String sender,
        String subject,
        int riskPercentage,
        String riskLevel,
        LocalDateTime analyzedAt,
        String summary
    ) {}
}
