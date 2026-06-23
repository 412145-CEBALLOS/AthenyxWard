package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UrgentLanguageRuleTest {

    private final UrgentLanguageRule rule = new UrgentLanguageRule();

    @Test
    void noUrgency_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello there", "sender@example.com", "Sender",
            "Just checking in with you.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void oneUrgencyKeyword_score15() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: Action required", "sender@example.com", "Sender",
            "Please verify your account immediately.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(15);
    }

    @Test
    void threeUrgencyKeywords_score45() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: Cuenta suspendida",
            "sender@example.com", "Sender",
            "Su cuenta ha sido suspendida. Verificar ahora o en 24 horas será cerrada.",
            "", java.util.List.of(), null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(45);
    }

    @Test
    void sixUrgencyKeywords_cappedAt60() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: ACTÚE YA - 24 HORAS",
            "sender@example.com", "Sender",
            "Urgente! Inmediato! Su cuenta suspendida! Verifique ahora! Sin demora! 24 horas!",
            "", java.util.List.of(), null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }

    @Test
    void urgentInSubjectOnly_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: Su paquete", "", "",
            "Normal email body", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isPresent();
    }
}
