package com.athenyx.backend.heuristics;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Aggregates a list of {@link HeuristicFinding}s into a single 0-100 risk
 * percentage plus a traffic-light {@link ThreatLevel}.
 *
 * <h2>Suma ponderada con boosts negativos (v2)</h2>
 *
 * <pre>
 *   raw = Σ ( finding.score * rule.severity.weight )
 *   pct = clamp( round(raw / 3), 0, 100 )
 * </pre>
 *
 * <p>Cada hallazgo aporta {@code score × weight}, donde {@code weight}
 * viene de su {@link RuleSeverity}. Una regla con severidad
 * {@code TRUST} aporta un valor <em>negativo</em> (por ejemplo
 * {@code AuthenticationPassRule} con score -10 y peso -1.0 suma
 * +10 al "raw", que se resta del total).</p>
 *
 * <p>El divisor 3 normaliza el resultado para que:</p>
 * <ul>
 *   <li>Una sola regla HIGH con score 85 (raw=102) quede en ~34% (GREEN).</li>
 *   <li>Tres reglas MEDIUM con score 50 (raw=150) lleguen a 50% (YELLOW).</li>
 *   <li>Cuatro reglas MEDIUM con score 70 (raw=280) lleguen a 93% (RED).</li>
 *   <li>Un email autenticado correctamente (raw reducido) baje a GREEN aunque
 *       tenga varias reglas ambiguas activas.</li>
 * </ul>
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
 * <h2>Empty findings</h2>
 *
 * <p>When the engine returns no findings the scorer short-circuits to
 * {@link HeuristicResult#safe()} (0 %, GREEN).</p>
 *
 * @see HeuristicResult
 * @see HeuristicFinding
 * @see RuleSeverity
 * @see ThreatLevel
 */
@Component
public class ThreatScorer {

    /** Divisor de normalización. Tres reglas MEDIUM de score 50 → 50%. */
    private static final int NORMALIZATION_DIVISOR = 3;

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

        double raw = 0;
        for (HeuristicFinding f : findings) {
            RuleSeverity sev = severityMap.getOrDefault(f.rule(), RuleSeverity.MEDIUM);
            raw += f.score() * sev.weight();
        }

        int pct = (int) Math.max(0, Math.min(100, Math.round(raw / NORMALIZATION_DIVISOR)));
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
