package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MassMailingServiceRuleTest {

    private final MassMailingServiceRule rule = new MassMailingServiceRule();

    @Test
    void normalEmail_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Meeting", "colleague@company.com", "Colleague",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void listUnsubscribeHeader_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Newsletter", "newsletter@mailchimp.com", "Sender",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null,
            "https://mailchimp.com/unsubscribe", null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }

    @Test
    void xMailerSendGrid_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Campaign", "campaign@company.com", "Company",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, "SendGrid"
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }

    @Test
    void xMailerMailchimp_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Update", "updates@company.com", "Company",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, "Mailchimp"
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }

    @Test
    void freeEmailWithMailingKeywords_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Mailchimp Campaign", "user@gmail.com", "User",
            "Newsletter content with campaign info", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }
}
