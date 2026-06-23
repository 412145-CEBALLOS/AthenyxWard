package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampAnalyzerTest {

    private final TimestampAnalyzer analyzer = new TimestampAnalyzer();

    @Test
    void validTimestamp_noAnomalies() {
        var headers = new ExtractedHeaders(
            null, null, null, null, null, null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0000", "+0000",
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        LocalDateTime received = LocalDateTime.now();
        var result = analyzer.analyze(headers, received);
        assertThat(result.futureDate()).isFalse();
        assertThat(result.timezoneAnomaly()).isFalse();
    }

    @Test
    void futureDate_detected() {
        var headers = new ExtractedHeaders(
            null, null, null, null, null, null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2099 10:00:00 +0000", "+0000",
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        LocalDateTime received = LocalDateTime.of(2099, 6, 22, 10, 0);
        var result = analyzer.analyze(headers, received);
        assertThat(result.futureDate()).isTrue();
    }

    @Test
    void timezoneAnomaly_detected() {
        var headers = new ExtractedHeaders(
            null, null, null, null, null, null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0000", "XXX_INVALID",
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = analyzer.analyze(headers, LocalDateTime.now());
        assertThat(result.timezoneAnomaly()).isTrue();
    }

    @Test
    void dateDrift_detected() {
        var headers = new ExtractedHeaders(
            null, null, null, null, null, null, null,
            java.util.List.of(),
            "Mon, 1 Jan 2020 10:00:00 +0000", "+0000",
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        LocalDateTime received = LocalDateTime.of(2026, 6, 22, 10, 0);
        var result = analyzer.analyze(headers, received);
        assertThat(result.dateDrift()).isTrue();
        assertThat(result.driftHours()).isGreaterThan(24);
    }

    @Test
    void numericOffset_notAnomaly() {
        var headers = new ExtractedHeaders(
            null, null, null, null, null, null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0530", "+0530",
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = analyzer.analyze(headers, LocalDateTime.now());
        assertThat(result.timezoneAnomaly()).isFalse();
    }
}
