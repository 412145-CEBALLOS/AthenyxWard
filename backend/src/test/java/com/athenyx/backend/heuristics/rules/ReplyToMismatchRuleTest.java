package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyToMismatchRuleTest {

    private final ReplyToMismatchRule rule = new ReplyToMismatchRule();

    @Test
    void noReplyTo_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void replyToMatchesSender_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "support@company.com", "Company",
            "content", "", java.util.List.of(),
            null, null, "support@company.com", null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void replyToDifferentDomain_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Account Update",
            "noreply@paypal.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, "support@paypal-verify.com", null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(80);
    }

    @Test
    void replyToWithFreeEmail_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "From Company",
            "contact@company.com", "Company Official",
            "content", "", java.util.List.of(),
            null, null, "helper@gmail.com", null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(80);
    }

    @Test
    void replyToNullOrBlank_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            null, null, "", null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
