package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortenedUrlRuleTest {

    private final ShortenedUrlRule rule = new ShortenedUrlRule();

    @Test
    void normalUrls_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Check this", "sender@example.com", "Sender",
            "content", "",
            java.util.List.of("https://www.google.com", "https://shop.example.com"),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void bitlyUrl_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Deal",
            "deals@spam.com", "Deals",
            "Amazing deal for you!",
            "",
            java.util.List.of("https://bit.ly/3x7fake"),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(30);
    }

    @Test
    void tinyurlUrl_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Offer",
            "offer@spam.com", "Offer",
            "Exclusive offer!",
            "",
            java.util.List.of("https://tinyurl.com/fakeoffer"),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(30);
    }

    @Test
    void multipleShortenedUrls_higherScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Multiple",
            "spam@spam.com", "Spam",
            "Check out these links!",
            "",
            java.util.List.of(
                "https://bit.ly/fake1",
                "https://tinyurl.com/fake2",
                "https://t.co/fake3"
            ),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }
}
