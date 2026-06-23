package com.athenyx.backend.heuristics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatScorerTest {

    private final ThreatScorer scorer;

    ThreatScorerTest() {
        var rules = List.of(
            new DummyHighRule(),
            new DummyMediumRule(),
            new DummyLowRule()
        );
        this.scorer = new ThreatScorer(rules);
    }

    @Test
    void emptyFindings_returnsGreenZero() {
        HeuristicResult result = scorer.score(List.of());
        assertThat(result.riskPercentage()).isEqualTo(0);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void singleHighRuleAtMax_returnsRed100() {
        var findings = List.of(new HeuristicFinding("HighRule", "high", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(100);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.RED);
    }

    @Test
    void mediumRuleAlone_returnsYellow40() {
        var findings = List.of(new HeuristicFinding("MediumRule", "medium", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(100);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.RED);
    }

    @Test
    void mixedRules_combinesCorrectly() {
        var findings = List.of(
            new HeuristicFinding("HighRule", "high", 50),
            new HeuristicFinding("MediumRule", "medium", 50)
        );
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isGreaterThan(40);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.YELLOW);
    }

    @Test
    void greenThreshold_below40() {
        var findings = List.of(new HeuristicFinding("LowRule", "low", 20));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void yellowThreshold_between40And69() {
        var findings = List.of(new HeuristicFinding("MediumRule", "medium", 50));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.YELLOW);
    }

    @Test
    void redThreshold_70AndAbove() {
        var findings = List.of(new HeuristicFinding("HighRule", "high", 70));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.RED);
    }

    @Test
    void resultContainsAllFindings() {
        var findings = List.of(
            new HeuristicFinding("HighRule", "desc1", 50),
            new HeuristicFinding("MediumRule", "desc2", 30)
        );
        HeuristicResult result = scorer.score(findings);
        assertThat(result.findings()).hasSize(2);
    }

    static class DummyHighRule implements HeuristicRule {
        public String name() { return "HighRule"; }
        public RuleSeverity severity() { return RuleSeverity.HIGH; }
        public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) { return Optional.empty(); }
    }

    static class DummyMediumRule implements HeuristicRule {
        public String name() { return "MediumRule"; }
        public RuleSeverity severity() { return RuleSeverity.MEDIUM; }
        public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) { return Optional.empty(); }
    }

    static class DummyLowRule implements HeuristicRule {
        public String name() { return "LowRule"; }
        public RuleSeverity severity() { return RuleSeverity.LOW; }
        public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) { return Optional.empty(); }
    }
}
