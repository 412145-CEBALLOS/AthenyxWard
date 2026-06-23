package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RiskyKeywordsRuleTest {

    private final RiskyKeywordsRule rule = new RiskyKeywordsRule();

    @Test
    void normalEmail_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Meeting", "colleague@company.com", "Colleague",
            "Let's meet tomorrow to discuss the project.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void bitcoinKeyword_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Investment Opportunity",
            "investor@crypto.com", "Investor",
            "Invest in Bitcoin today for guaranteed returns!", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(15);
    }

    @Test
    void wireTransferKeyword_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Payment",
            "finance@company.com", "Finance",
            "Please arrange a wire transfer to the following account.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(15);
    }

    @Test
    void giftCardKeyword_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Gift Card Offer",
            "offers@company.com", "Offers",
            "Buy gift cards at a discount!", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }

    @Test
    void westernUnion_triggers() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Transfer",
            "agent@westernunion.com", "Western Union",
            "Send money via Western Union.", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
    }

    @Test
    void multipleRiskyKeywords_cappedAt50() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENT: Money",
            "agent@scam.com", "Agent",
            "Wire transfer required immediately. Bitcoin investment. Western Union. Gift card.",
            "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(50);
    }
}
