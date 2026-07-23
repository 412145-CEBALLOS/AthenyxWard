package com.athenyx.backend.dto;

import java.time.LocalDateTime;

public enum StatsPeriod {
    WEEK(7),
    MONTH(30),
    YEAR(365);

    private final int days;

    StatsPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    public static StatsPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return WEEK;
        }
        for (StatsPeriod p : values()) {
            if (p.name().equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Período no válido: " + value);
    }

    public DateRange currentRange(LocalDateTime now) {
        return new DateRange(now.minusDays(days), now);
    }

    public DateRange previousRange(LocalDateTime now) {
        LocalDateTime previousTo = now.minusDays(days);
        return new DateRange(previousTo.minusDays(days), previousTo);
    }

    public record DateRange(LocalDateTime from, LocalDateTime to) {
    }
}
