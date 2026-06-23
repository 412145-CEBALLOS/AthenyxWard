package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScamLanguagePatternRuleTest {

    private final ScamLanguagePatternRule rule = new ScamLanguagePatternRule();

    @Test
    void normalEmail_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Meeting tomorrow", "colleague@company.com", "Colleague",
            "Hey, let's meet tomorrow at 3pm.", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void chineseLotteryScam_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Prize", "winner@lottery.com", "Lottery",
            "Ha ganado un premio!", "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void transferenciaBancariaAndHerencia_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENT BUSINESS",
            "agent@nigeria.gov",
            "Agent",
            "Ha sido seleccionado. Necesito transferencia bancaria para mover la herencia. " +
            "Mande sus datos de cuenta bancaria inmediatamente.",
            "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void loteriaAndTransferencia_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Felicidades",
            "sorteo@noreply.com",
            "Sorteo",
            "Ha ganado la lotería! Transferencia bancaria disponible. Solicite sus datos de cuenta para reclamar.",
            "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void multipleScamPatterns_cappedAt70() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "GANASTE",
            "winner@lotto.com",
            "Lottery",
            "Felicidades! Ha ganado! Premio! Lotería! Herencia! Transferencia! " +
            "Millones! Dólares! Bono! Regalo! Solicite ahora!",
            "", java.util.List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(70);
    }
}
