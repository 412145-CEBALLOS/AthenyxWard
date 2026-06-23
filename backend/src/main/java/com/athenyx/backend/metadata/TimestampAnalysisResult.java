package com.athenyx.backend.metadata;

public record TimestampAnalysisResult(
    boolean futureDate,
    boolean dateDrift,
    boolean timezoneAnomaly,
    long driftHours
) {}
