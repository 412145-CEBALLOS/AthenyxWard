package com.athenyx.backend.heuristics;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ThreatScorer {

    private static final int GREEN_THRESHOLD = 40;
    private static final int YELLOW_THRESHOLD = 70;

    private final Map<String, RuleSeverity> severityMap;

    public ThreatScorer(List<HeuristicRule> rules) {
        this.severityMap = Map.ofEntries(
            rules.stream()
                 .map(r -> Map.entry(r.name(), r.severity()))
                 .toArray(Map.Entry[]::new)
        );
    }

    public HeuristicResult score(List<HeuristicFinding> findings) {
        if (findings.isEmpty()) {
            return HeuristicResult.safe();
        }

        double weighted = 0;
        double maxPossible = 0;

        for (HeuristicFinding f : findings) {
            RuleSeverity sev = severityMap.getOrDefault(f.rule(), RuleSeverity.MEDIUM);
            weighted += f.score() * sev.weight();
            maxPossible += 100 * sev.weight();
        }

        int pct = (int) Math.min(100, Math.round((float) (weighted / maxPossible * 100)));
        ThreatLevel level = determineRiskLevel(pct);

        return new HeuristicResult(findings, pct, level);
    }

    public ThreatLevel determineRiskLevel(int riskPercentage) {
        if (riskPercentage < GREEN_THRESHOLD) {
            return ThreatLevel.GREEN;
        }
        if (riskPercentage < YELLOW_THRESHOLD) {
            return ThreatLevel.YELLOW;
        }
        return ThreatLevel.RED;
    }

    public int getGreenThreshold() { return GREEN_THRESHOLD; }
    public int getYellowThreshold() { return YELLOW_THRESHOLD; }
}
