package com.athenyx.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserStatsResponse(
    String period,
    List<KpiMetric> kpis,
    List<DailyCount> dailyThreats,
    List<RiskBucket> riskDistribution,
    List<CategoryCount> topCategories,
    List<RecentItem> recentActivity,
    LocalDateTime lastThreatAt,
    TrialUsage trialUsage
) {
    public record KpiMetric(
        String label,
        double value,
        double previousValue,
        double trendPercent,
        boolean trendUp
    ) {
    }

    public record DailyCount(String label, int count) {
    }

    public record RiskBucket(String level, int count) {
    }

    public record CategoryCount(String category, int count) {
    }

    public record RecentItem(
        String date,
        String sender,
        int risk,
        String level
    ) {
    }

    public record TrialUsage(int used, int total) {
    }
}
