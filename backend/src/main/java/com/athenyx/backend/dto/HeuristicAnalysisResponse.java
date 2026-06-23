package com.athenyx.backend.dto;

import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;

import java.time.LocalDateTime;
import java.util.List;

public record HeuristicAnalysisResponse(
    Long analysisId,
    Long emailId,
    int riskPercentage,
    ThreatLevel riskLevel,
    List<ThreatCategoryDto> threatCategories,
    List<HeuristicFindingDto> heuristicFindings,
    List<SuspiciousUrlDto> suspiciousUrls,
    SenderTrustDto senderTrust,
    String aiExplanation,
    String contentSummary,
    List<RecommendedActionDto> recommendedActions,
    LocalDateTime analyzedAt,
    AnalysisOrigin source,
    String modelName
) {
    public record HeuristicFindingDto(String rule, String description, int score) {}
    public record SuspiciousUrlDto(String raw, String resolvedDomain, String reason) {}
    public record SenderTrustDto(
        String sender,
        String displayName,
        String domain,
        boolean displayMismatch,
        String spf,
        String dkim,
        String dmarc,
        String returnPath,
        String replyTo,
        String massMailingProvider,
        boolean timezoneAnomaly,
        String trustLevel,
        int trustScore
    ) {}
    public record RecommendedActionDto(String label, boolean destructive, boolean premiumOnly) {}
    public record ThreatCategoryDto(String category, String label) {}
}
