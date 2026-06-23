package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlFormRuleTest {

    private final HtmlFormRule rule = new HtmlFormRule();

    @Test
    void noHtml_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "Just plain text", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void htmlFormWithPasswordAndHttp_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Verify your account",
            "security@bank.com", "Bank",
            "Please verify.",
            "<form action='http://fake-bank.com/login'><input name='password'></form>",
            java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(95);
    }

    @Test
    void htmlFormWithCreditCard_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Payment details",
            "billing@shop.com", "Shop",
            "Please enter your payment info.",
            "<form action='https://shop.com/pay'><input name='cc'></form>",
            java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(70);
    }

    @Test
    void htmlFormWithHttp_noSensitiveInput_mediumScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Survey",
            "survey@company.com", "Company",
            "Please fill out our survey.",
            "<form action='http://survey.com/form'><input name='email'></form>",
            java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(70);
    }

    @Test
    void htmlFormWithHttpsOnly_lowScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Newsletter signup",
            "news@site.com", "Site",
            "Subscribe here.",
            "<form action='https://site.com/subscribe'><input name='email'></form>",
            java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }
}
