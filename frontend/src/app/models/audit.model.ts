export type AuditSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AuditResult = 'SUCCESS' | 'FAILURE';
export type AuditActionType =
  | 'LOGIN'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  | 'TOKEN_REFRESH_FAILED'
  | 'ROLE_CHANGE'
  | 'USER_DEACTIVATED'
  | 'USER_REACTIVATED'
  | 'USER_DELETED'
  | 'TRIAL_RESET'
  | 'CONFIG_UPDATE'
  | 'CONFIG_PURGE'
  | 'PHISHING_DETECTED'
  | 'AUTO_ANALYSIS'
  | 'EMAIL_MARKED_IMPORTANT'
  | 'EMAIL_HIDDEN'
  | 'EMAIL_UNHIDDEN'
  | 'EMAIL_DELETED'
  | 'REMINDER_CREATED'
  | 'REMINDER_UPDATED'
  | 'REMINDER_DELETED'
  | 'REMINDER_DONE'
  | 'EXPORT_CSV'
  | 'SESSION_REVOKED';

export interface AuditEntryResponse {
  id: number;
  createdAt: string;
  actorId: number | '';
  actorEmail: string;
  actorRole: string;
  actionType: AuditActionType;
  targetType: string;
  targetId: string;
  severity: AuditSeverity;
  result: AuditResult;
  payload: string;
  ipAddress: string;
  userAgent: string;
  correlationId: string;
}

export interface AuditPageResponse {
  items: AuditEntryResponse[];
  currentPage: number;
  totalPages: number;
  totalItems: number;
}

export interface AuditFilters {
  from?: string;
  to?: string;
  actor?: string;
  action?: AuditActionType;
  severity?: AuditSeverity;
  query?: string;
  page?: number;
  size?: number;
}
