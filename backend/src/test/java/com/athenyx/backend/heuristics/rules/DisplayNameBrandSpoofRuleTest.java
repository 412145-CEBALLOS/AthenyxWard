package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNameBrandSpoofRuleTest {

    private final DisplayNameBrandSpoofRule rule = new DisplayNameBrandSpoofRule();

    @Test
    void genericSoporteWithNonBrand_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Account Alert",
            "user@unknownsite.com", "Soporte de Banco",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(35);
    }

    @Test
    void securityTeamWithNonBrand_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Security Alert",
            "user@strange-domain.com", "Microsoft Security Team",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(35);
    }

    @Test
    void noreplyWithLegitDomain_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "No Reply",
            "no-reply@google.com", "No Reply",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void legitSecurityTeam_doesNotTrigger() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Security Alert",
            "security@microsoft.com", "Microsoft Security",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void nullDisplayName_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Alert", "sender@unknown.com", null,
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void nintendoWhitelisted_doesNotTrigger() {
        // no-reply@accounts.nintendo.com used to be a HIGH false positive;
        // the trusted-sender whitelist now silences it.
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Receipt", "no-reply@accounts.nintendo.com", "No Reply",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void paypalWhitelisted_doesNotTrigger() {
        // communications.paypal.com is a known PayPal marketing subdomain.
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Statement", "service@communications.paypal.com", "PayPal Service",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void claudeWhitelisted_doesNotTrigger() {
        // email.claude.com legitimately uses "Claude Team" as display name.
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Update", "noreply@email.claude.com", "Claude Team",
            "content", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }
}
