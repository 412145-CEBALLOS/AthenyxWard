package com.athenyx.backend.heuristics;

/**
 * Severity buckets for heuristic rules.
 *
 * <p>The {@link #weight()} is multiplied by the rule's individual score
 * before being summed by {@link ThreatScorer}.</p>
 *
 * <h2>Buckets</h2>
 * <table>
 *   <caption>Weight per severity</caption>
 *   <tr><th>Severity</th><th>Weight</th><th>Use case</th></tr>
 *   <tr><td>HIGH</td><td>+1.2</td><td>Strong indicators (malicious URL, attachment, brand impersonation).</td></tr>
 *   <tr><td>MEDIUM</td><td>+1.0</td><td>Default — most heuristic signals.</td></tr>
 *   <tr><td>LOW</td><td>+0.7</td><td>Soft signals (minor language cues, generic metadata).</td></tr>
 *   <tr><td>TRUST</td><td>-1.0</td><td><strong>Legitimacy</strong> signals that subtract from the score (SPF/DKIM/DMARC pass).</td></tr>
 * </table>
 */
public enum RuleSeverity {
    HIGH(1.2),
    MEDIUM(1.0),
    LOW(0.7),
    TRUST(-1.0);

    private final double weight;

    RuleSeverity(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
