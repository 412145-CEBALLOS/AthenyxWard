package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnPathMismatchRuleTest {

    private final ReturnPathMismatchRule rule = new ReturnPathMismatchRule();

    private EmailHeuristicsInput build(String sender, String returnPath) {
        return new EmailHeuristicsInput(
            "Test", sender, "Sender",
            "content", "", java.util.List.of(),
            null, null, null, returnPath, null, null, null, null, null, null
        );
    }

    @Test
    void noReturnPath_returnsEmpty() {
        assertThat(rule.apply(build("sender@paypal.com", null)).isPresent()).isFalse();
    }

    @Test
    void matchingDomains_returnsEmpty() {
        assertThat(rule.apply(build("sender@paypal.com", "<paypal.com>")).isPresent()).isFalse();
    }

    @Test
    void mismatchingDomains_realPhishing_triggers() {
        Optional<HeuristicFinding> result = rule.apply(
            build("sender@random-corp.example", "<scammer@random-scammer.ru>"));
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(45);
    }

    @Test
    void nintendoViaAmazonSes_doesNotTrigger() {
        // Nintendo legitimately relays through Amazon SES.
        Optional<HeuristicFinding> result = rule.apply(build(
            "no-reply@accounts.nintendo.com",
            "<0101019ee766c0dd-fa30350f-015b-4d5c-8f72-8909b2262571-000000@us-west-2.amazonses.com>"
        ));
        assertThat(result).isEmpty();
    }

    @Test
    void googleViaScoutcamp_doesNotTrigger() {
        // Google One uses scoutcamp.bounces.google.com as Return-Path.
        Optional<HeuristicFinding> result = rule.apply(build(
            "googleone-noreply@google.com",
            "<349TQaREKACYIQQINGQPG-PQTGRNaIQQING.EQO@scoutcamp.bounces.google.com>"
        ));
        assertThat(result).isEmpty();
    }

    @Test
    void paypalViaSendGrid_doesNotTrigger() {
        // PayPal mailing campaigns often route through SendGrid.
        Optional<HeuristicFinding> result = rule.apply(build(
            "service@paypal.com",
            "<bounce+xyz@bounce.sendgrid.net>"
        ));
        assertThat(result).isEmpty();
    }

    @Test
    void unknownSenderViaAmazonSes_stillTriggers() {
        // A sender NOT in the corporate whitelist but using Amazon SES
        // is still suspicious (the Return-Path bypasses the whitelist check
        // because the ESP is trusted but the visible From is not).
        Optional<HeuristicFinding> result = rule.apply(build(
            "ceo@random-company.ru",
            "<abc@us-west-2.amazonses.com>"
        ));
        assertThat(result).isPresent();
    }
}
