/**
 * Three-bucket traffic-light classification. The thresholds are
 * defined by the backend's heuristics/AI layer.
 */
export type RiskLevel = 'GREEN' | 'YELLOW' | 'RED';

/** Origin of a risk score. */
export type AnalysisSource = 'HEURISTIC' | 'AI' | 'HYBRID';

/**
 * UI state of the analysis pane for a single email. Used by the
 * email-viewer to render the right affordance.
 */
export type AnalysisState =
  | 'idle'
  | 'loading'
  | 'ready'
  | 'error'
  | 'unavailable-trial';

/** Categories the detector can flag. */
export type ThreatCategory =
  | 'PHISHING'
  | 'SPOOFING'
  | 'MALWARE'
  | 'SOCIAL_ENGINEERING'
  | 'DANGEROUS_LINK'
  | 'FRAUD'
  | 'ACCOUNT_THEFT'
  | 'AI_GENERATED';

/**
 * Spanish labels for every {@link ThreatCategory}. Centralised so the
 * UI is consistent and a future i18n migration can swap the values for
 * a translation loader.
 */
export const THREAT_CATEGORY_LABELS: Record<ThreatCategory, string> = {
  PHISHING: 'Phishing',
  SPOOFING: 'Suplantación',
  MALWARE: 'Malware',
  SOCIAL_ENGINEERING: 'Ingeniería social',
  DANGEROUS_LINK: 'Enlace peligroso',
  FRAUD: 'Fraude',
  ACCOUNT_THEFT: 'Robo de cuenta',
  AI_GENERATED: 'Generado por IA',
};

/**
 * A single rule that fired against the email, with a human-readable
 * description and a contribution to the overall risk score.
 */
export interface HeuristicFinding {
  rule: string;
  description: string;
  score: number;
}

/** A URL the detector found suspicious, with the reason. */
export interface SuspiciousUrl {
  raw: string;
  resolvedDomain?: string;
  reason: string;
}

/** Trust signals derived from the sender's identity / authentication. */
export interface SenderTrust {
  sender: string;
  displayName: string;
  domain: string;
  displayMismatch: boolean;
  spf?: 'PASS' | 'FAIL' | 'NEUTRAL';
  dkim?: 'PASS' | 'FAIL' | 'NEUTRAL';
  dmarc?: 'PASS' | 'FAIL' | 'NEUTRAL';
  returnPath?: string | null;
  replyTo?: string | null;
  massMailingProvider?: string | null;
  timezoneAnomaly?: boolean;
  trustLevel?: 'TRUSTED' | 'UNKNOWN' | 'SUSPICIOUS' | null;
  trustScore?: number;
}

/** A suggested next-step action the UI can render as a button. */
export interface RecommendedAction {
  label: string;
  destructive?: boolean;
  premiumOnly?: boolean;
}

/**
 * Full payload returned by the analysis endpoint. The viewer renders
 * the risk percentage, traffic light, threat categories, AI narrative
 * and the recommended actions.
 */
export interface EmailAnalysisResult {
  analysisId: number;
  emailId: number;
  riskPercentage: number;
  riskLevel: RiskLevel;
  threatCategories: ThreatCategory[];
  heuristicFindings: HeuristicFinding[];
  suspiciousUrls: SuspiciousUrl[];
  senderTrust: SenderTrust;
  aiExplanation: string;
  contentSummary: string;
  recommendedActions: RecommendedAction[];
  analyzedAt: string;
  source: AnalysisSource;
  modelName?: string;
}
