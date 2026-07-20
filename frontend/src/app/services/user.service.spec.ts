import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { environment } from '../../environments/environment';

const mockUsage = {
  user: { id: 1, name: 'Test', email: 't@t.com', pictureUrl: '', role: 'TRIAL' as const,
    trialEndDate: null, trialExpired: false, accessibilityMode: true, termsAcceptedAt: null,
    termsVersion: null, lastLoginAt: null, emailVerified: null },
  analysis: { used: 5, limit: 20, trialEndDate: null, expired: false },
  reminders: { active: 2, done: 0 },
  emails: { total: 10, important: 1, hidden: 0, deleted: 0 },
  sessions: { active: 1 },
  dataInventory: { emails: 10, analyses: 5, aiExplanations: 2, reminders: 5, auditEvents: 20, oldestRecordAt: null },
};

const mockSessions = [
  { id: 1, familyId: 'f1', userAgent: 'Chrome', ip: '1.1.1.1', issuedAt: '2025-01-01', lastUsedAt: '2025-06-01', current: true },
  { id: 2, familyId: 'f2', userAgent: 'Firefox', ip: '2.2.2.2', issuedAt: '2025-01-02', lastUsedAt: '2025-06-02', current: false },
];

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('getUsage', () => {
    it('returns UserUsage from API', () => {
      service.getUsage().subscribe((r) => {
        expect(r.analysis.used).toBe(5);
        expect(r.dataInventory.emails).toBe(10);
      });
      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me/usage`);
      req.flush(mockUsage);
    });
  });

  describe('getSessions', () => {
    it('returns active sessions from API', () => {
      service.getSessions().subscribe((r) => {
        expect(r.length).toBe(2);
        expect(r[0].current).toBe(true);
      });
      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me/sessions`);
      req.flush(mockSessions);
    });
  });

  describe('revokeSession', () => {
    it('calls DELETE /api/auth/me/sessions/:id', () => {
      service.revokeSession(99).subscribe();
      const req = httpMock.expectOne(`${environment.apiUrl}/auth/me/sessions/99`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });
});
