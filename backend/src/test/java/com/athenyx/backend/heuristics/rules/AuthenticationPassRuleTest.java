package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import com.athenyx.backend.heuristics.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationPassRuleTest {

    private final AuthenticationPassRule rule = new AuthenticationPassRule();

    @Test
    void name_isAuthenticationPassRule() {
        assertThat(rule.name()).isEqualTo("AuthenticationPassRule");
    }

    @Test
    void severity_isTRUSTWithNegativeWeight() {
        assertThat(rule.severity()).isEqualTo(RuleSeverity.TRUST);
        assertThat(rule.severity().weight()).isEqualTo(-1.0);
    }

    @Test
    void noPassHeaders_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, "fail", "fail", "fail", null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void spfPass_triggersWithPositiveScore() {
        // Positive score — the TRUST weight (-1.0) flips the sign in
        // the scorer so it actually subtracts from the total.
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, "pass", null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(10);
    }

    @Test
    void dkimPass_triggersWithPositiveScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, null, "pass", null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(10);
    }

    @Test
    void dmarcPass_triggersWithPositiveScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, null, null, "pass", null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(15);
    }

    @Test
    void allThreePass_sumTo35() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, "pass", "pass", "pass", null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(35);
    }

    @Test
    void caseInsensitive() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Test", "a@b.com", "S", "c", "", java.util.List.of(),
            null, null, null, null, "PASS", null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }
}
