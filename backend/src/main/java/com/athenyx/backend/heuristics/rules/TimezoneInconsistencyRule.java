package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TimezoneInconsistencyRule implements HeuristicRule {

    private static final Set<String> KNOWN_TIMEZONES = Set.of(
        "UTC", "GMT", "EST", "EDT", "CST", "CDT", "MST", "MDT", "PST", "PDT",
        "AKST", "AKDT", "HST", "HAST", "AST", "ADT", "NST", "NDT",
        "WEST", "CEST", "EEST", "JST", "KST", "AEST", "NZST", "NZDT",
        "LMT", "UT"
    );

    private static final Pattern NUMERIC_OFFSET = Pattern.compile("^[+-]\\d{2}:?\\d{2}$");

    @Override
    public String name() {
        return "TimezoneInconsistencyRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        if (input.originalTimezone() == null || input.originalTimezone().isBlank()) {
            return Optional.empty();
        }

        String tz = input.originalTimezone().trim();

        if (NUMERIC_OFFSET.matcher(tz).matches()) {
            return Optional.empty();
        }

        if (KNOWN_TIMEZONES.contains(tz.toUpperCase()) || KNOWN_TIMEZONES.contains(tz)) {
            return Optional.empty();
        }

        boolean isFutureDate = checkFutureDate(input);
        int score = isFutureDate ? 60 : 40;
        String desc = isFutureDate
            ? "Zona horaria desconocida ('" + tz + "') + fecha en el futuro"
            : "Zona horaria anómala o desconocida en Date header: '" + tz + "'";

        return Optional.of(new HeuristicFinding(name(), desc, score));
    }

    private boolean checkFutureDate(EmailHeuristicsInput input) {
        if (input.originalDateHeader() == null || input.receivedAt() == null) return false;
        LocalDateTime parsed = parseDate(input.originalDateHeader());
        if (parsed == null) return false;
        return parsed.isAfter(LocalDateTime.now().plusMinutes(5));
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return ZonedDateTime.parse(dateStr.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                String cleaned = dateStr.trim()
                    .replaceAll("\\s+\\([A-Z]{2,5}\\)\\s*$", "")
                    .replaceAll("\\s+\\(UTC\\)\\s*$", "").trim();
                if (cleaned.contains(",")) {
                    cleaned = cleaned.substring(cleaned.indexOf(',') + 1).trim();
                }
                DateTimeFormatter[] formats = {
                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXX", java.util.Locale.US)
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
