package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnPathMismatchRuleTest {

    private final ReturnPathMismatchRule rule = new ReturnPathMismatchRule();

    @Test
    void noReturnPath_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "sender@paypal.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void matchingDomains_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "sender@paypal.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, null, "<paypal.com>", null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void mismatchingDomains_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "sender@paypal.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, null, "<scammer@gmail.com>", null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(85);
    }

    @Test
    void returnPathWithAngleBrackets_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "support@amazon.com", "Amazon",
            "content", "", java.util.List.of(),
            null, null, null, "<fake-amazon.com>", null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(85);
    }
}
