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
        assertThat(result.get().score()).isEqualTo(85);
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
        assertThat(result.get().score()).isEqualTo(85);
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
}
