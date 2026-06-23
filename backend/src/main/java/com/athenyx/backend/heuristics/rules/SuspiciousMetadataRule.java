package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
public class SuspiciousMetadataRule implements HeuristicRule {

    private static final DateTimeFormatter HEADER_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss");

    @Override
    public String name() {
        return "SuspiciousMetadataRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        int indicators = 0;
        StringBuilder description = new StringBuilder();

        if (isFutureDate(input.receivedAt())) {
            indicators++;
            description.append("Fecha de recepción en el futuro, ");
        }

        if (input.originalDateHeader() != null && input.receivedAt() != null) {
            LocalDateTime parsedHeaderDate = parseHeaderDate(input.originalDateHeader());
            if (parsedHeaderDate != null) {
                long driftHours = Math.abs(java.time.Duration.between(
                    input.receivedAt(), parsedHeaderDate
                ).toHours());

                if (driftHours > 24) {
                    indicators++;
                    description.append("Diferencia mayor a 24 horas entre Date header y Received, ");
                }
            }
        }

        if (input.originalDateHeader() == null || input.originalDateHeader().isBlank()) {
            indicators++;
            description.append("Header Date ausente o vacío, ");
        }

        if (indicators == 0) {
            return Optional.empty();
        }

        int score = indicators == 1 ? 50 : 80;
        String desc = description.length() > 2
            ? "Metadatos anómalos: " + description.substring(0, description.length() - 2)
            : "Metadatos anómalos detectados";

        return Optional.of(new HeuristicFinding(name(), desc, score));
    }

    private boolean isFutureDate(LocalDateTime date) {
        if (date == null) return false;
        return date.isAfter(LocalDateTime.now().plusMinutes(5));
    }

    private LocalDateTime parseHeaderDate(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            return LocalDateTime.parse(header.trim(), HEADER_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
