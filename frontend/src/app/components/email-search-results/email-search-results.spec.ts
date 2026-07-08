import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { EmailSearchResultsComponent } from './email-search-results';
import { EmailSearchService } from '../../services/email-search.service';
import { EmailSummary, EmailPageResponse } from '../../models/email-summary.model';

function emptyResponse(): EmailPageResponse {
  return { emails: [], currentPage: 0, pageSize: 20, hasNextPage: false };
}

function makeEmail(overrides: Partial<EmailSummary> = {}): EmailSummary {
  return {
    id: 10,
    gmailId: 'g10',
    sender: 'a@b.com',
    senderName: 'A',
    subject: 'Subject 10',
    snippet: 'snip 10',
    receivedAt: '2026-07-06T10:00:00',
    fetchedAt: '2026-07-06T10:00:00',
    isRead: false,
    originalDateHeader: null,
    isImportant: false,
    isHidden: false,
    isDeleted: false,
    riskPercentage: null,
    riskLevel: null,
    reminder: null,
    ...overrides,
  };
}

describe('EmailSearchResultsComponent', () => {
  let component: EmailSearchResultsComponent;
  let fixture: ComponentFixture<EmailSearchResultsComponent>;
  let httpMock: HttpTestingController;
  let emailSearch: EmailSearchService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailSearchResultsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'home', component: EmailSearchResultsComponent }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailSearchResultsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    emailSearch = TestBed.inject(EmailSearchService);
  });

  afterEach(() => {
    try {
      while (httpMock.match(() => true).length > 0) {
        for (const r of httpMock.match(() => true)) r.flush(emptyResponse());
      }
    } catch { /* no requests left */ }
    httpMock.verify();
  });

  it('does not render the dropdown when isOpen=false', fakeAsync(() => {
    emailSearch.set('foo');
    emailSearch.open();
    emailSearch.close();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.search-results-dropdown')).toBeFalsy();
  }));

  it('does not render the dropdown when term is empty even if isOpen=true', fakeAsync(() => {
    emailSearch.set('');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);
    expect(fixture.nativeElement.querySelector('.search-results-dropdown')).toBeFalsy();
  }));

  it('renders the empty state when results are empty and term is set', fakeAsync(() => {
    emailSearch.set('zzz');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);
    const req = httpMock.expectOne((r) => r.params.get('q') === 'zzz');
    req.flush({ ...emptyResponse(), emails: [] });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.search-results-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('No hay resultados para');
    expect(empty.textContent).toContain('"zzz"');
  }));

  it('renders the result list after a debounced fetch lands', fakeAsync(() => {
    emailSearch.set('pay');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);

    const req = httpMock.expectOne(
      (r) => r.url === '/api/emails/fetch' && r.params.get('q') === 'pay' && r.params.get('size') === '20',
    );
    req.flush({
      emails: [makeEmail({ id: 1, subject: 'PayPal receipt' })],
      currentPage: 0,
      pageSize: 20,
      hasNextPage: false,
    });
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.search-result-item');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('PayPal receipt');
  }));

  it('renders the "Ver todos los resultados" footer when there are results', fakeAsync(() => {
    emailSearch.set('foo');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);
    httpMock.expectOne((r) => r.params.get('q') === 'foo').flush({
      emails: [makeEmail()],
      currentPage: 0,
      pageSize: 20,
      hasNextPage: false,
    });
    fixture.detectChanges();

    const viewAll = fixture.nativeElement.querySelector('.search-results-view-all');
    expect(viewAll).toBeTruthy();
    expect(viewAll.textContent).toContain('Ver todos los resultados para');
    expect(viewAll.textContent).toContain('"foo"');
  }));

  it('click on a result closes the dropdown and navigates to the email', fakeAsync(() => {
    emailSearch.set('foo');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);
    httpMock.expectOne((r) => r.params.get('q') === 'foo').flush({
      emails: [makeEmail({ id: 42 })],
      currentPage: 0,
      pageSize: 20,
      hasNextPage: false,
    });
    fixture.detectChanges();

    const item = fixture.nativeElement.querySelector('.search-result-item');
    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(emailSearch.isOpen()).toBeFalse();
  }));

  it('"Ver todos" closes the dropdown and triggers applyToInbox', fakeAsync(() => {
    emailSearch.set('foo');
    emailSearch.open();
    fixture.detectChanges();
    tick(300);
    httpMock.expectOne((r) => r.params.get('q') === 'foo').flush({
      emails: [makeEmail()],
      currentPage: 0,
      pageSize: 20,
      hasNextPage: false,
    });
    fixture.detectChanges();

    const seen: string[] = [];
    const sub = emailSearch.inboxApply$.subscribe((v) => seen.push(v));

    const viewAll = fixture.nativeElement.querySelector('.search-results-view-all');
    viewAll.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(emailSearch.isOpen()).toBeFalse();
    tick();
    expect(seen).toEqual(['foo']);
    sub.unsubscribe();
  }));
});
