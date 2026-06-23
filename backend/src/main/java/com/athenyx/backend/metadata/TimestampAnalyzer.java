package com.athenyx.backend.metadata;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TimestampAnalyzer {

    private static final Set<String> ANOMALOUS_TIMEZONES = Set.of(
        "LMT", "UT", "UTC", "GMT", "EST", "EDT", "CST", "CDT", "MST", "MDT", "PST", "PDT",
        "AKST", "AKDT", "HST", "HAST", "AST", "ADT", "NST", "NDT", "PHT", "PHOT",
        "KST", "JST", "CST_CHINA", "CCT", "IRST", "IRDT", "EET", "EEST", "CAT",
        "NZST", "NZDT", "ChST", "BOT", "BRT", "GST", "PKT"
    );

    private static final Pattern NUMERIC_OFFSET = Pattern.compile("[+-]\\d{2}:?\\d{2}$");

    public TimestampAnalysisResult analyze(ExtractedHeaders headers, LocalDateTime receivedAt) {
        boolean futureDate = checkFutureDate(headers, receivedAt);
        boolean dateDrift = checkDateDrift(headers, receivedAt);
        boolean timezoneAnomaly = checkTimezoneAnomaly(headers);
        long driftHours = calculateDriftHours(headers, receivedAt);
        return new TimestampAnalysisResult(futureDate, dateDrift, timezoneAnomaly, driftHours);
    }

    private boolean checkFutureDate(ExtractedHeaders headers, LocalDateTime receivedAt) {
        if (headers.originalDateHeader() == null || receivedAt == null) return false;
        LocalDateTime parsed = parseHeaderDate(headers.originalDateHeader());
        if (parsed == null) return false;
        return parsed.isAfter(LocalDateTime.now().plusMinutes(5));
    }

    private boolean checkDateDrift(ExtractedHeaders headers, LocalDateTime receivedAt) {
        if (headers.originalDateHeader() == null || receivedAt == null) return false;
        LocalDateTime parsed = parseHeaderDate(headers.originalDateHeader());
        if (parsed == null) return false;
        long driftHours = Math.abs(
            java.time.Duration.between(receivedAt, parsed).toHours()
        );
        return driftHours > 24;
    }

    private boolean checkTimezoneAnomaly(ExtractedHeaders headers) {
        String tz = headers.originalTimezone();
        if (tz == null || tz.isBlank()) return false;
        String cleaned = tz.trim();
        if (NUMERIC_OFFSET.matcher(cleaned).find()) {
            return false;
        }
        return !ANOMALOUS_TIMEZONES.contains(cleaned.toUpperCase())
            && !ANOMALOUS_TIMEZONES.contains(cleaned);
    }

    private long calculateDriftHours(ExtractedHeaders headers, LocalDateTime receivedAt) {
        if (headers.originalDateHeader() == null || receivedAt == null) return 0;
        LocalDateTime parsed = parseHeaderDate(headers.originalDateHeader());
        if (parsed == null) return 0;
        return Math.abs(java.time.Duration.between(receivedAt, parsed).toHours());
    }

    private LocalDateTime parseHeaderDate(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            return ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                String cleaned = header.trim()
                    .replaceAll("\\s+\\([A-Z]{2,5}\\)\\s*$", "")
                    .replaceAll("\\s+\\(UTC\\)\\s*$", "")
                    .trim();
                if (cleaned.contains(",")) {
                    cleaned = cleaned.substring(cleaned.indexOf(',') + 1).trim();
                }
                DateTimeFormatter[] formats = {
                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXX", java.util.Locale.US),
                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", java.util.Locale.US)
                };
                for (DateTimeFormatter fmt : formats) {
                    try {
                        return ZonedDateTime.parse(cleaned, fmt)
                            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                    } catch (DateTimeParseException ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
