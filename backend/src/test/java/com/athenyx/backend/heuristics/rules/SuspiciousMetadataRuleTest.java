package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousMetadataRuleTest {

    private final SuspiciousMetadataRule rule = new SuspiciousMetadataRule();

    @Test
    void normalDates_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void futureDate_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now().plusDays(5), "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void missingDateHeader_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), null,
            null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void multipleAnomalies_higherScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now().plusDays(10), null,
            null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(80);
    }
}
