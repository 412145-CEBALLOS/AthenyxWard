package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SenderImpersonationRuleTest {

    private final SenderImpersonationRule rule = new SenderImpersonationRule();

    @Test
    void freeEmailWithPaypalDisplayName_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "PayPal: Confirm your identity",
            "securityalert@gmail.com",
            "PayPal Security",
            "Please confirm your account", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void freeEmailWithAmazonDisplayName_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Amazon Order",
            "order-confirm@outlook.com",
            "Amazon",
            "Your order has shipped", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void legitAmazonEmail_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Amazon Order Confirmed",
            "ship-confirm@amazon.com",
            "Amazon",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void nullDisplayName_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Subject", "sender@gmail.com", null,
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void brandInNameButFreeEmail_triggersMediumScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Netflix: Your statement",
            "billing@protonmail.com",
            "Netflix Support",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void nonBrandDomainNoMatch_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello",
            "friend@company.com",
            "Friend",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
