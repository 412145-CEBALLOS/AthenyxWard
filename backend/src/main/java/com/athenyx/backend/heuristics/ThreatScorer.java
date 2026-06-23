package com.athenyx.backend.heuristics;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Aggregates a list of {@link HeuristicFinding}s into a single 0-100 risk
 * percentage plus a traffic-light {@link ThreatLevel}.
 *
 * <h2>Weighted aggregation</h2>
 *
 * <p>Each rule carries a {@link RuleSeverity severity} that weights its
 * contribution to the final score. The aggregation formula is:</p>
 *
 * <pre>
 *   weighted       = Σ ( finding.score * rule.severity.weight )
 *   maxPossible    = Σ ( 100 * rule.severity.weight )
 *   riskPercentage = round( weighted / maxPossible * 100 )      // clamped to [0, 100]
 * </pre>
 *
 * <h2>Severity weights</h2>
 * <table>
 *   <caption>Weights per severity</caption>
 *   <tr><th>Severity</th><th>Weight</th><th>Rationale</th></tr>
 *   <tr><td>HIGH</td><td>1.2</td><td>Strong indicators (e.g. malicious URL, attachment).</td></tr>
 *   <tr><td>MEDIUM</td><td>1.0</td><td>Default — most heuristic signals.</td></tr>
 *   <tr><td>LOW</td><td>0.7</td><td>Soft signals (e.g. minor language cues).</td></tr>
 * </table>
 *
 * <h2>Traffic-light thresholds</h2>
 * <table>
 *   <caption>Risk-level classification</caption>
 *   <tr><th>Risk %</th><th>Level</th><th>UI label</th></tr>
 *   <tr><td>0 – 39</td><td>{@link ThreatLevel#GREEN}</td><td>Seguro</td></tr>
 *   <tr><td>40 – 69</td><td>{@link ThreatLevel#YELLOW}</td><td>Sospechoso</td></tr>
 *   <tr><td>70 – 100</td><td>{@link ThreatLevel#RED}</td><td>Peligroso</td></tr>
 * </table>
 *
 * <h2>Worked example</h2>
 *
 * <p>Two findings: a HIGH rule scoring 100 and a MEDIUM rule scoring 50.</p>
 * <pre>
 *   weighted    = 100 * 1.2 + 50 * 1.0 = 170
 *   maxPossible = 100 * 1.2 + 100 * 1.0 = 220
 *   pct         = round( 170 / 220 * 100 ) = 77
 *   level       = RED  (≥ 70)
 * </pre>
 *
 * <h2>Empty findings</h2>
 *
 * <p>When the engine returns no findings the scorer short-circuits to
 * {@link HeuristicResult#safe()} (0 %, GREEN). A non-empty list of
 * low-severity findings can still produce a non-zero percentage.</p>
 *
 * @see HeuristicResult
 * @see HeuristicFinding
 * @see RuleSeverity
 * @see ThreatLevel
 */
@Component
public class ThreatScorer {

    /** Risk percentage below this value is considered safe (GREEN). */
    private static final int GREEN_THRESHOLD = 40;

    /** Risk percentage at or above this value is considered dangerous (RED). */
    private static final int YELLOW_THRESHOLD = 70;

    private final Map<String, RuleSeverity> severityMap;

    public ThreatScorer(List<HeuristicRule> rules) {
        this.severityMap = Map.ofEntries(
            rules.stream()
                 .map(r -> Map.entry(r.name(), r.severity()))
                 .toArray(Map.Entry[]::new)
        );
    }

    /**
     * Scores a list of heuristic findings into a {@link HeuristicResult}.
     *
     * @param findings findings emitted by the {@link HeuristicEngine}; may be empty
     * @return immutable result with risk %, traffic light and the original findings
     */
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

    /**
     * Maps a 0-100 risk percentage to a traffic-light level.
     *
     * @param riskPercentage integer in [0, 100]
     * @return the matching {@link ThreatLevel}
     */
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
