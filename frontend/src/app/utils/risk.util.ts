import { RiskLevel } from '../models/email-analysis.model';

/**
 * Risk-percentage thresholds for the traffic-light classification.
 * Mirrors the backend {@code ThreatScorer} constants:
 * {@code GREEN_THRESHOLD = 40}, {@code YELLOW_THRESHOLD = 70}.
 */
export const RISK_THRESHOLDS = {
  GREEN_MAX: 40,
  YELLOW_MAX: 70,
} as const;

/**
 * Maps a 0-100 risk percentage to a traffic-light {@link RiskLevel}.
 *
 * <p>Buckets:</p>
 * <ul>
 *   <li>{@code riskPercentage < 40} → {@code 'GREEN'} (safe)</li>
 *   <li>{@code 40 ≤ riskPercentage < 70} → {@code 'YELLOW'} (suspicious)</li>
 *   <li>{@code riskPercentage ≥ 70} → {@code 'RED'} (dangerous)</li>
 * </ul>
 *
 * <p>{@code null} / {@code undefined} defaults to {@code 'GREEN'} so the
 * caller can pass optional fields without explicit null-checks.</p>
 */
export function riskLevelFromPercentage(
  pct: number | null | undefined,
): RiskLevel {
  if (pct == null) return 'GREEN';
  if (pct < RISK_THRESHOLDS.GREEN_MAX) return 'GREEN';
  if (pct < RISK_THRESHOLDS.YELLOW_MAX) return 'YELLOW';
  return 'RED';
}
