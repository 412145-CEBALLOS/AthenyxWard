package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneInconsistencyRuleTest {

    private final TimezoneInconsistencyRule rule = new TimezoneInconsistencyRule();

    @Test
    void standardTimezone_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null, "+0000", null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void numericOffset_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), "Mon, 22 Jun 2026 10:00:00 +0530",
            null, null, null, null, null, "+0530", null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void nullTimezone_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), "Mon, 22 Jun 2026 10:00:00 GMT",
            null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void unknownTimezone_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now(), "Mon, 22 Jun 2026 10:00:00 XXX_INVALID",
            null, null, null, null, null, "XXX_INVALID", null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void futureDateWithUnknownTimezone_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            LocalDateTime.now().plusDays(10), "Mon, 22 Jun 2099 10:00:00 +0000",
            null, null, null, null, null, "INVALID_TZ", null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }
}
