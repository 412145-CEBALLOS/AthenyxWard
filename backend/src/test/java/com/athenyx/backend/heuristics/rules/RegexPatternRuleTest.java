package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RegexPatternRuleTest {

    private final RegexPatternRule rule = new RegexPatternRule();

    @Test
    void noSensitiveData_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "This is a normal email without any sensitive data.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void fakeCreditCardNumber_returnsEmptyBecauseLuhnFails() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Payment", "support@service.com", "Service",
            "Your card ending in 4111111111111112 has been rejected.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void validLuhnCreditCard_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Payment Confirmation",
            "billing@amazon.com", "Amazon",
            "Your card ending in 4111111111111111 has been charged $99.00.",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(70);
    }

    @Test
    void ibanPattern_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Transfer",
            "bank@secure.com", "Bank",
            "Please send payment to IBAN: ES9121000418450200051332",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(70);
    }

    @Test
    void dniPattern_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Identity",
            "hr@company.com", "HR",
            "Please provide your ID: 12345678A",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }

    @Test
    void ssnPattern_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Tax Form",
            "tax@agency.com", "Tax Agency",
            "Your SSN: 123-45-6789",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }

    @Test
    void multiplePatterns_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Payment",
            "bank@secure.com", "Bank",
            "Card: 4111111111111111. IBAN: ES9121000418450200051332. ID: 12345678A",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(95);
    }
}
