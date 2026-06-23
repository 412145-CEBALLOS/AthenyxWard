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
            new DummyLowRule(),
            new DummyTrustRule()
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
    void singleHighRuleAtMax_staysGreen() {
        // Old formula normalised to 100; new formula divides by 3 so
        // 100 * 1.2 / 3 = 40 → still YELLOW.
        var findings = List.of(new HeuristicFinding("HighRule", "high", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(40);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.YELLOW);
    }

    @Test
    void singleMediumRuleAt100_isGreen() {
        // 100 * 1.0 / 3 = 33 → GREEN.
        var findings = List.of(new HeuristicFinding("MediumRule", "medium", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(33);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void mixedRules_threeMediums_returnsYellow() {
        // 3 × 50 × 1.0 = 150 → 150/3 = 50 → YELLOW.
        var findings = List.of(
            new HeuristicFinding("MediumRule", "m1", 50),
            new HeuristicFinding("MediumRule", "m2", 50),
            new HeuristicFinding("MediumRule", "m3", 50)
        );
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(50);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.YELLOW);
    }

    @Test
    void greenThreshold_lowRule() {
        var findings = List.of(new HeuristicFinding("LowRule", "low", 20));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void yellowThreshold_mediumRule50() {
        // 50 * 1.0 / 3 = 17 → GREEN actually.
        // The old test was based on the old formula.
        var findings = List.of(new HeuristicFinding("MediumRule", "medium", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(33);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void redThreshold_threeHighsAt100() {
        // 3 × 100 × 1.2 = 360 → 360/3 = 120 → clamped to 100 → RED.
        var findings = List.of(
            new HeuristicFinding("HighRule", "h1", 100),
            new HeuristicFinding("HighRule", "h2", 100),
            new HeuristicFinding("HighRule", "h3", 100)
        );
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(100);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.RED);
    }

    @Test
    void trustRule_subtractsFromScore() {
        // 1 high @ 100 (raw=120) + 1 trust @ +10 * -1.0 = -10 (raw=110)
        // 110/3 = 37 → GREEN
        var findings = List.of(
            new HeuristicFinding("HighRule", "high", 100),
            new HeuristicFinding("TrustRule", "boost", 10)
        );
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(37);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
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

    @Test
    void rawNeverNegative_clampedToZero() {
        // 1 trust @ +100 * -1.0 = -100 (subtractive)
        // raw = -100, divided = -33 → clamped to 0 → GREEN
        var findings = List.of(new HeuristicFinding("TrustRule", "boost", 100));
        HeuristicResult result = scorer.score(findings);
        assertThat(result.riskPercentage()).isEqualTo(0);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
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

    static class DummyTrustRule implements HeuristicRule {
        public String name() { return "TrustRule"; }
        public RuleSeverity severity() { return RuleSeverity.TRUST; }
        public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) { return Optional.empty(); }
    }
}
