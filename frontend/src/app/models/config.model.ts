export type ConfigType = 'INT' | 'BOOLEAN' | 'STRING';

export interface ConfigEntry {
  key: string;
  value: string;
  type: ConfigType;
  description: string;
  category: string;
  categoryLabel: string;
  minValue: number | null;
  maxValue: number | null;
  publiclyVisible: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface ConfigCategory {
  category: string;
  categoryLabel: string;
  entries: ConfigEntry[];
}

export interface PurgeResult {
  purgedCount: number;
  skippedDueToReminders?: number;
  executedAt: string;
  durationMs: number;
}

export interface RiskThresholds {
  low: number;
  medium: number;
}
