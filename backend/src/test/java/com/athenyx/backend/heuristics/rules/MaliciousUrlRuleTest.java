package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MaliciousUrlRuleTest {

    private final MaliciousUrlRule rule = new MaliciousUrlRule();

    @Test
    void normalUrl_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Check this", "sender@example.com", "Sender",
            "content", "",
            java.util.List.of("https://www.google.com/search?q=test"),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void ipDirectUrl_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Login", "admin@example.com", "Admin",
            "Please login",
            "",
            java.util.List.of("http://192.168.1.1/login"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void urlWithAtSign_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Verify", "sender@example.com", "Sender",
            "content", "",
            java.util.List.of("https://google.com@evil.com/login"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void manySubdomains_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@secure.bank.example.com", "Secure Bank",
            "content", "",
            java.util.List.of("https://secure.bank.example.com.login.evil.com/verify"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void dangerousTldZip_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Download", "sender@example.com", "Sender",
            "content", "",
            java.util.List.of("https://file.example.zip/download"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }

    @Test
    void multipleIndicators_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@example.com", "Sender",
            "content", "",
            java.util.List.of(
                "http://1.2.3.4:8080/login",
                "https://a.b.c.d.evil.com@attacker.com/login"
            ),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(80);
    }
}
