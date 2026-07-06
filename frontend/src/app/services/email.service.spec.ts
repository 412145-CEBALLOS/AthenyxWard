import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmailService } from './email.service';

describe('EmailService', () => {
  let service: EmailService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(EmailService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('fetchEmails', () => {
    it('calls GET /api/emails/fetch?page=0 when q is not provided', () => {
      service.fetchEmails(0).subscribe();
      const req = httpMock.expectOne((r) => r.url === '/api/emails/fetch');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.has('q')).toBeFalse();
      req.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });
    });

    it('appends q=foo to the URL when q is provided', () => {
      service.fetchEmails(0, 'paypal').subscribe();
      const req = httpMock.expectOne((r) =>
        r.url === '/api/emails/fetch'
        && r.params.get('page') === '0'
        && r.params.get('q') === 'paypal'
      );
      expect(req.request.method).toBe('GET');
      req.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });
    });

    it('trims q before sending', () => {
      service.fetchEmails(0, '  paypal  ').subscribe();
      const req = httpMock.expectOne((r) => r.params.get('q') === 'paypal');
      req.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });
    });

    it('omits q when it is null, undefined or blank', () => {
      service.fetchEmails(0, null).subscribe();
      const req1 = httpMock.expectOne((r) => !r.params.has('q'));
      req1.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });

      service.fetchEmails(0, undefined).subscribe();
      const req2 = httpMock.expectOne((r) => !r.params.has('q'));
      req2.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });

      service.fetchEmails(0, '   ').subscribe();
      const req3 = httpMock.expectOne((r) => !r.params.has('q'));
      req3.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });
    });

    it('appends size=N to the URL when size is provided', () => {
      service.fetchEmails(0, 'foo', 8).subscribe();
      const req = httpMock.expectOne((r) =>
        r.url === '/api/emails/fetch'
          && r.params.get('q') === 'foo'
          && r.params.get('size') === '8',
      );
      req.flush({ emails: [], currentPage: 0, pageSize: 8, hasNextPage: false });
    });

    it('omits size when null, undefined, or zero/negative', () => {
      service.fetchEmails(0, undefined, null).subscribe();
      const req1 = httpMock.expectOne((r) => !r.params.has('size'));
      req1.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });

      service.fetchEmails(0, undefined, 0).subscribe();
      const req2 = httpMock.expectOne((r) => !r.params.has('size'));
      req2.flush({ emails: [], currentPage: 0, pageSize: 20, hasNextPage: false });
    });
  });

  describe('fetchImportantEmails', () => {
    it('calls GET /api/emails/important', () => {
      const mockEmails = [
        { id: 1, gmailId: 'g1', sender: 'a@b.com', senderName: 'A', subject: 'S',
          snippet: 'snip', receivedAt: '2026-06-01', fetchedAt: '2026-06-01',
          isRead: false, originalDateHeader: null, isImportant: true },
      ];

      service.fetchImportantEmails().subscribe((emails) => {
        expect(emails).toEqual(mockEmails);
      });

      const req = httpMock.expectOne('/api/emails/important');
      expect(req.request.method).toBe('GET');
      req.flush(mockEmails);
    });
  });

  describe('refreshImportantCount', () => {
    it('calls GET /api/emails/important/count and updates signal', () => {
      service.refreshImportantCount();

      const req = httpMock.expectOne('/api/emails/important/count');
      expect(req.request.method).toBe('GET');
      req.flush({ count: 5 });
      expect(service.importantCount()).toBe(5);
    });
  });

  describe('toggleImportant', () => {
    it('calls POST /api/emails/{id}/important and increments count when marked', () => {
      service.importantCount.set(3);

      service.toggleImportant(10).subscribe((res) => {
        expect(res.emailId).toBe(10);
        expect(res.isImportant).toBe(true);
      });

      const req = httpMock.expectOne('/api/emails/10/important');
      expect(req.request.method).toBe('POST');
      req.flush({ emailId: 10, isImportant: true });
      expect(service.importantCount()).toBe(4);
    });

    it('calls POST /api/emails/{id}/important and decrements count when unmarked', () => {
      service.importantCount.set(3);

      service.toggleImportant(10).subscribe((res) => {
        expect(res.isImportant).toBe(false);
      });

      const req = httpMock.expectOne('/api/emails/10/important');
      req.flush({ emailId: 10, isImportant: false });
      expect(service.importantCount()).toBe(2);
    });
  });
});
