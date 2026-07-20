import { UserInfo } from './user-info.model';

export interface UserUsage {
  user: UserInfo;
  analysis: AnalysisUsage;
  reminders: ReminderUsage;
  emails: EmailUsage;
  sessions: SessionUsage;
  dataInventory: DataInventory;
}

export interface AnalysisUsage {
  used: number;
  limit: number | null;
  trialEndDate: string | null;
  expired: boolean;
}

export interface ReminderUsage {
  active: number;
  done: number;
}

export interface EmailUsage {
  total: number;
  important: number;
  hidden: number;
  deleted: number;
}

export interface SessionUsage {
  active: number;
}

export interface DataInventory {
  emails: number;
  analyses: number;
  aiExplanations: number;
  reminders: number;
  auditEvents: number;
  oldestRecordAt: string | null;
}
