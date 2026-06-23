package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLoginPageRuleTest {

    private final FakeLoginPageRule rule = new FakeLoginPageRule();

    @Test
    void normalUrl_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Newsletter", "newsletter@example.com", "Newsletter",
            "Check out our latest products.",
            "<html><body><a href='https://shop.example.com'>Shop</a></body></html>",
            java.util.List.of("https://shop.example.com/page"),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void loginPathInUrl_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Verify your account",
            "security@bank.com", "Bank",
            "Please verify your account.",
            "",
            java.util.List.of("https://bank-verify.com/login?redirect=https://bank.com"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void formWithPasswordInHttp_triggersHighScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Verify your account",
            "security@bank.com", "Bank",
            "Please verify.",
            "<form action='http://evil.com/login'><input name='password'></form>",
            java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(80);
    }

    @Test
    void emptyUrlsAndHtml_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Subject", "sender@example.com", "Sender",
            "Content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
