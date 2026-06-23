package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousTldRuleTest {

    private final SuspiciousTldRule rule = new SuspiciousTldRule();

    @Test
    void normalDomain_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void senderWithZipTld_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Download",
            "sender@file.zip", "File Share",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(75);
    }

    @Test
    void urlWithClickTld_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Offer",
            "sender@normal.com", "Sender",
            "content", "",
            java.util.List.of("https://deal.click/offer"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(40);
    }

    @Test
    void multipleUrlsWithSuspiciousTld_mediumScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Links",
            "sender@normal.com", "Sender",
            "content", "",
            java.util.List.of(
                "https://site.xyz/file",
                "https://file.top/download"
            ),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(40);
    }

    @Test
    void safeTlds_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello",
            "sender@safe.org", "Sender",
            "content", "",
            java.util.List.of("https://safe.com/page", "https://safe.net/download"),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
