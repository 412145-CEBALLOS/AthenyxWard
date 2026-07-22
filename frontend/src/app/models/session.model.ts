export interface ActiveSession {
  id: number;
  familyId: string;
  userAgent: string | null;
  ip: string | null;
  issuedAt: string;
  lastUsedAt: string | null;
  current: boolean;
}
