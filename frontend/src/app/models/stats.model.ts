export type StatsPeriod = 'week' | 'month' | 'year';

export interface KpiMetric {
  label: string;
  value: number;
  previousValue: number;
  trendPercent: number;
  trendUp: boolean;
}

export interface DailyCount {
  label: string;
  count: number;
}

export interface RiskBucket {
  level: 'GREEN' | 'YELLOW' | 'RED';
  count: number;
}

export interface CategoryCount {
  category: string;
  count: number;
}

export interface RecentAnalysisItem {
  date: string;
  sender: string;
  risk: number;
  level: 'GREEN' | 'YELLOW' | 'RED';
}

export interface TrialUsage {
  used: number;
  total: number;
}

export interface UserStatsResponse {
  period: StatsPeriod;
  kpis: KpiMetric[];
  dailyThreats: DailyCount[];
  riskDistribution: RiskBucket[];
  topCategories: CategoryCount[];
  recentActivity: RecentAnalysisItem[];
  lastThreatAt: string | null;
  trialUsage: TrialUsage | null;
}

export interface RoleBucket {
  role: 'ADMIN' | 'PREMIUM' | 'TRIAL';
  count: number;
}

export interface SourceBucket {
  source: string;
  count: number;
}

export interface EngagementMetrics {
  dau: number;
  wau: number;
  mau: number;
}

export interface ConversionRate {
  value: number;
  previousValue: number;
  trendPercent: number;
  trendUp: boolean;
}

export interface HourBucket {
  hour: number;
  count: number;
}

export interface AdminStatsResponse {
  period: StatsPeriod;
  kpis: KpiMetric[];
  dailyThreats: DailyCount[];
  riskDistribution: RiskBucket[];
  userSplit: RoleBucket[];
  topCategories: CategoryCount[];
  analysisSourceSplit: SourceBucket[];
  engagement: EngagementMetrics;
  conversionRate: ConversionRate;
  signups: DailyCount[];
  threatsByHour: HourBucket[];
}

export const ANALYSIS_SOURCE_LABELS: Record<string, string> = {
  Heurística: 'Heurística',
  IA: 'IA',
  Híbrido: 'Híbrido',
};

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  PREMIUM: 'Premium',
  TRIAL: 'Prueba',
};
