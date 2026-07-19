import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuditService } from './audit.service';

describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getEntries', () => {
    it('calls GET /api/admin/audit with page and size', () => {
      const mockResponse = {
        items: [],
        currentPage: 0,
        totalPages: 0,
        totalItems: 0,
      };
      service.getEntries({ page: 0, size: 20 }).subscribe();
      const req = httpMock.expectOne((r) => r.url === '/api/admin/audit');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      req.flush(mockResponse);
    });

    it('appends from and to params when provided', () => {
      const mockResponse = { items: [], currentPage: 0, totalPages: 0, totalItems: 0 };
      service.getEntries({ from: '2026-07-01', to: '2026-07-13' }).subscribe();
      const req = httpMock.expectOne((r) =>
        r.params.get('from') === '2026-07-01' && r.params.get('to') === '2026-07-13'
      );
      req.flush(mockResponse);
    });

    it('omits params when not provided', () => {
      const mockResponse = { items: [], currentPage: 0, totalPages: 0, totalItems: 0 };
      service.getEntries({ page: 0 }).subscribe();
      const req = httpMock.expectOne((r) =>
        r.url === '/api/admin/audit' && !r.params.has('from') && !r.params.has('to')
      );
      req.flush(mockResponse);
    });

    it('appends actor and action params when provided', () => {
      const mockResponse = { items: [], currentPage: 0, totalPages: 0, totalItems: 0 };
      service.getEntries({ actor: 'u@test.com', action: 'LOGIN' as any }).subscribe();
      const req = httpMock.expectOne((r) =>
        r.params.get('actor') === 'u@test.com' && r.params.get('action') === 'LOGIN'
      );
      req.flush(mockResponse);
    });

    it('appends query param when provided', () => {
      const mockResponse = { items: [], currentPage: 0, totalPages: 0, totalItems: 0 };
      service.getEntries({ query: 'phishing' }).subscribe();
      const req = httpMock.expectOne((r) => r.params.get('query') === 'phishing');
      req.flush(mockResponse);
    });
  });

  describe('getExportUrl', () => {
    it('returns URL with from and to params', () => {
      const url = service.getExportUrl({ from: '2026-07-01', to: '2026-07-13' });
      expect(url).toContain('from=2026-07-01');
      expect(url).toContain('to=2026-07-13');
    });

    it('returns URL with all filter params', () => {
      const url = service.getExportUrl({
        from: '2026-07-01',
        to: '2026-07-13',
        actor: 'u@test.com',
        action: 'LOGIN' as any,
        severity: 'CRITICAL' as any,
      });
      expect(url).toContain('from=2026-07-01');
      expect(url).toContain('to=2026-07-13');
      expect(url).toContain('actor=u%40test.com');
      expect(url).toContain('action=LOGIN');
      expect(url).toContain('severity=CRITICAL');
    });

    it('omits undefined and empty values', () => {
      const url = service.getExportUrl({ actor: '' });
      expect(url).not.toContain('actor=');
    });
  });
});
