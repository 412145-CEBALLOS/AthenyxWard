package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FreeEmailProviderBrandRuleTest {

    private final FreeEmailProviderBrandRule rule = new FreeEmailProviderBrandRule();

    @Test
    void gmailWithAmazonBrand_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Amazon: Your order has shipped",
            "john@gmail.com", "Amazon",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(90);
    }

    @Test
    void outlookWithPaypalBrand_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "PayPal: Payment received",
            "mike@outlook.com", "PayPal",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(90);
    }

    @Test
    void gmailWithoutBrand_triggersEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello from John",
            "john@gmail.com", "John",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void corporateDomain_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Amazon: Your order",
            "order@amazon.com", "Amazon",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void brandInDisplayNameOnly_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello",
            "friend@gmail.com", "Netflix Support",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }
}
