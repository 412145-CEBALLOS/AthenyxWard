package com.athenyx.backend.dto;

import java.util.List;

public record AdminStatsResponse(
    String period,
    List<KpiMetric> kpis,
    List<DailyCount> dailyThreats,
    List<RiskBucket> riskDistribution,
    List<RoleBucket> userSplit,
    List<CategoryCount> topCategories,
    List<SourceBucket> analysisSourceSplit,
    EngagementMetrics engagement,
    ConversionRate conversionRate,
    List<DailyCount> signups,
    List<HourBucket> threatsByHour
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

    public record RoleBucket(String role, int count) {
    }

    public record CategoryCount(String category, int count) {
    }

    public record SourceBucket(String source, int count) {
    }

    public record EngagementMetrics(int dau, int wau, int mau) {
    }

    public record ConversionRate(
        double value,
        double previousValue,
        double trendPercent,
        boolean trendUp
    ) {
    }

    public record HourBucket(int hour, int count) {
    }
}
