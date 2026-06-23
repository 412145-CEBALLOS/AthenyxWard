package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousDomainRuleTest {

    private final SuspiciousDomainRule rule = new SuspiciousDomainRule();

    @Test
    void safeEmail_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "legit@gmail.com", "John", "content", "html", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void paypalTyposquattingDomain_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Account Update", "support@paypal-security.com", "Someone",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().rule()).isEqualTo("SuspiciousDomainRule");
        assertThat(result.get().score()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void bancomerTyposquatting_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alerta", "security@bancomer-verify.com", "Someone",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().rule()).isEqualTo("SuspiciousDomainRule");
    }

    @Test
    void officialPaypalDomain_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Receipt", "service@paypal.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void nullSender_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Subject", null, null, "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void gmailDomain_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "friend@gmail.com", "Friend",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
