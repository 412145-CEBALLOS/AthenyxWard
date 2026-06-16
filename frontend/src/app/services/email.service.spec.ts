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
