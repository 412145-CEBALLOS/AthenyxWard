import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Location } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';
import { HomeComponent } from './home';
import { AuthService } from '../../services/auth.service';
import { UserInfo } from '../../models/user-info.model';
import { EmailPageResponse, EmailSummary, EmailDetail } from '../../models/email-summary.model';
import { EmailSearchService } from '../../services/email-search.service';
import { EmailAnalysisResult } from '../../models/email-analysis.model';


function emptyResponse(): EmailPageResponse {
  return { emails: [], currentPage: 0, pageSize: 20, hasNextPage: false };
}

function makeUser(overrides: Partial<UserInfo> = {}): UserInfo {
  return {
    id: 1,
    name: 'Test User',
    email: 'test@example.com',
    pictureUrl: '',
    role: 'PREMIUM',
    trialEndDate: null,
    trialExpired: false,
    accessibilityMode: true,
    termsAcceptedAt: null,
    termsVersion: null,
    ...overrides,
  };
}

function makeRouteWithParams(initial: Record<string, string> = {}) {
  const subject = new BehaviorSubject<ReturnType<typeof convertToParamMap>>(
    convertToParamMap(initial),
  );
  const snapshot = { queryParamMap: convertToParamMap(initial) };
  return {
    route: {
      queryParamMap: subject.asObservable() as Observable<ReturnType<typeof convertToParamMap>>,
      snapshot,
    } as unknown as ActivatedRoute,
    emit: (params: Record<string, string>) => subject.next(convertToParamMap(params)),
    complete: () => subject.complete(),
  };
}

describe('HomeComponent â€” US 3.7 search bar (desktop default)', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let location: Location;
  let auth: AuthService;
  let emailSearch: EmailSearchService;
  let routeHandle: ReturnType<typeof makeRouteWithParams>;

  beforeEach(async () => {
    routeHandle = makeRouteWithParams();

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'home', component: HomeComponent }]),
        { provide: ActivatedRoute, useValue: routeHandle.route },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    auth = TestBed.inject(AuthService);
    emailSearch = TestBed.inject(EmailSearchService);
    auth.currentUser.set(makeUser());

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    flushAll();
    httpMock.verify();
  });

  function flushAll(): void {
    while (httpMock.match(() => true).length > 0) {
      for (const r of httpMock.match(() => true)) {
        r.flush(emptyResponse());
      }
    }
  }

  function initAndFlush(): void {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/fetch');
    req.flush(emptyResponse());
    flushAll();
  }

  it('does NOT render a search input inside the email-box header', () => {
    initAndFlush();
    const local = fixture.nativeElement.querySelector('.email-box input[type="search"]');
    expect(local).toBeFalsy();
  });

  it('desktop: typing does NOT refetch the inbox (the dropdown handles it)', fakeAsync(() => {
    initAndFlush();
    emailSearch.set('pay');
    tick(300);
    // Desktop: switchMap returns EMPTY for the inbox; the
    // dropdown component (not under test here) would issue the
    // actual request. The inbox must stay untouched.
    const withQ = httpMock.match(
      (r) => r.url === '/api/emails/fetch' && r.params.has('q'),
    );
    expect(withQ.length).toBe(0);
    flushAll();
  }));

  it('desktop: Enter (inboxApply$) DOES refetch the inbox immediately', fakeAsync(() => {
    initAndFlush();
    emailSearch.applyToInbox('paypal');
    const req = httpMock.expectOne(
      (r) => r.url === '/api/emails/fetch' && r.params.get('q') === 'paypal',
    );
    req.flush(emptyResponse());
    expect(component.currentQuery()).toBe('paypal');
  }));

  it('does NOT re-fetch when queryParamMap echoes a value we just wrote', fakeAsync(() => {
    initAndFlush();
    emailSearch.set('paypal');
    tick(300);
    flushAll();

    routeHandle.emit({ q: 'paypal' });
    tick(300);
    const withQ = httpMock.match(
      (r) => r.url === '/api/emails/fetch' && r.params.has('q'),
    );
    expect(withQ.length).toBe(0);
    flushAll();
  }));

  it('desktop: clearing the search resets currentQuery to empty', fakeAsync(() => {
    initAndFlush();

    // 1. User types a query â€” the debounce fires and currentQuery is set to 'zzz'.
    emailSearch.set('zzz');
    tick(300);
    fixture.detectChanges();
    expect(component.currentQuery()).toBe('zzz');

    // 2. User clears the search bar â€” the debounce fires and with the fix,
    // currentQuery resets to '' (instead of staying at 'zzz').
    emailSearch.set('');
    tick(300);
    fixture.detectChanges();
    expect(component.currentQuery()).toBe('');
    flushAll();
  }));

  it('desktop: typing a new search does NOT refetch the inbox (only the dropdown)', fakeAsync(() => {
    initAndFlush();
    emailSearch.set('pay');
    tick(300);
    // Desktop: switchMap returns EMPTY for the inbox when term is non-empty.
    // The dropdown component (not under test here) issues the actual request.
    const withQ = httpMock.match(
      (r) => r.url === '/api/emails/fetch' && r.params.has('q'),
    );
    expect(withQ.length).toBe(0);
    flushAll();
  }));

  it('renders the search empty state when results are empty and q is non-empty', fakeAsync(() => {
    initAndFlush();
    emailSearch.applyToInbox('zzz');
    const req = httpMock.expectOne((r) => r.params.get('q') === 'zzz');
    req.flush(emptyResponse());
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.email-empty-search');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('No se encontraron correos para');
    expect(empty.textContent).toContain('"zzz"');
  }));

  it('renders the original empty state when q is empty and there are no emails', () => {
    initAndFlush();
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.email-empty:not(.email-empty-search)');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('No hay correos aÃºn');
  });

  it('does NOT show the trial-expired modal for ADMIN users even when trialExpired=true', () => {
    auth.currentUser.set(makeUser({ role: 'ADMIN', trialExpired: true, trialEndDate: '2020-01-01T00:00:00Z' }));
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/emails/fetch').flush(emptyResponse());
    flushAll();
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.modal-card');
    expect(modal).toBeFalsy();
  });

  it('does NOT show the trial-expired modal for PREMIUM users even when trialExpired=true', () => {
    auth.currentUser.set(makeUser({ role: 'PREMIUM', trialExpired: true, trialEndDate: '2020-01-01T00:00:00Z' }));
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/emails/fetch').flush(emptyResponse());
    flushAll();
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.modal-card');
    expect(modal).toBeFalsy();
  });

  it('shows the trial-expired modal for TRIAL users when trialExpired=true', () => {
    auth.currentUser.set(makeUser({ role: 'TRIAL', trialExpired: true, trialEndDate: '2020-01-01T00:00:00Z' }));
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('.modal-card');
    expect(modal).toBeTruthy();
    expect(modal.textContent).toContain('PerÃ­odo de prueba terminado');
  });
});

describe('HomeComponent â€” US 3.7 search bar (mobile)', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let emailSearch: EmailSearchService;
  let routeHandle: ReturnType<typeof makeRouteWithParams>;

  beforeEach(async () => {
    routeHandle = makeRouteWithParams();

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'home', component: HomeComponent }]),
        { provide: ActivatedRoute, useValue: routeHandle.route },
      ],
    }).compileComponents();

    auth = TestBed.inject(AuthService);
    emailSearch = TestBed.inject(EmailSearchService);
    auth.currentUser.set(makeUser());

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    flushAll();
    httpMock.verify();
  });

  function flushAll(): void {
    while (httpMock.match(() => true).length > 0) {
      for (const r of httpMock.match(() => true)) r.flush(emptyResponse());
    }
  }

  it('mobile: typing DOES refetch the inbox live (switchMap fires fetchEmails$)', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/emails/fetch').flush(emptyResponse());
    flushAll();
    component.isMobile.set(true);

    emailSearch.set('foo');
    tick(300);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/emails/fetch' && r.params.get('q') === 'foo',
    );
    req.flush(emptyResponse());
    expect(component.currentQuery()).toBe('foo');
  }));

  it('mobile: cancels the in-flight fetch when the user types a new value', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/emails/fetch').flush(emptyResponse());
    flushAll();
    component.isMobile.set(true);

    emailSearch.set('foo');
    tick(300);
    httpMock.expectOne(
      (r) => r.url === '/api/emails/fetch' && r.params.get('q') === 'foo',
    );

    emailSearch.set('bar');
    tick(300);
    const matches = httpMock.match(() => true);
    expect(matches.length).toBe(1);
    expect(matches[0].request.params.get('q')).toBe('bar');
    matches[0].flush(emptyResponse());
  }));
});

describe('HomeComponent â€” bootstrapAnalysis (US 2.8 TRIAL cache restoration)', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let auth: AuthService;
  let routeHandle: ReturnType<typeof makeRouteWithParams>;

  const makeDetail = (id: number): EmailDetail => ({
    id,
    gmailId: 'gmail-1',
    sender: 'phishing@bad.com',
    senderName: 'Bad Actor',
    subject: 'Urgent: Action required',
    snippet: 'Click this link now!',
    contentForAnalysis: 'Click this link now!',
    htmlContent: null,
    receivedAt: '2026-07-01T10:00:00Z',
    fetchedAt: '2026-07-01T10:05:00Z',
    isRead: false,
    originalDateHeader: null,
    isImportant: false,
    isHidden: false,
    isDeleted: false,
  });

  const makeCachedAnalysis = (emailId: number): EmailAnalysisResult => ({
    analysisId: 1,
    emailId,
    riskPercentage: 85,
    riskLevel: 'RED',
    threatCategories: ['PHISHING'],
    heuristicFindings: [{ rule: 'SuspiciousSender', description: 'Sender domain is suspicious', score: 30 }],
    suspiciousUrls: [],
    senderTrust: {
      sender: 'phishing@bad.com',
      displayName: 'Bad Actor',
      domain: 'bad.com',
      displayMismatch: false,
    },
    aiExplanation: 'This email is highly suspicious.',
    contentSummary: 'Click this link now!',
    recommendedActions: [],
    analyzedAt: '2026-07-01T10:10:00Z',
    source: 'HEURISTIC',
  });

  beforeEach(async () => {
    routeHandle = makeRouteWithParams();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'home', component: HomeComponent }]),
        { provide: ActivatedRoute, useValue: routeHandle.route },
      ],
    }).compileComponents();
    auth = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
  });

  /**
   * Calls bootstrapAnalysis directly (private method) so we bypass
   * all the init HTTP requests from fetchEmails$ and focus purely on
   * the analysis-restoration logic.
   */
  function callBootstrap(detail: EmailDetail, summary: EmailSummary): void {
    (component as any)['bootstrapAnalysis'](detail, summary);
  }

  it('TRIAL + summary.riskLevel != null + getLatest returns cached â†’ state ready', fakeAsync(() => {
    auth.currentUser.set(makeUser({ role: 'TRIAL' }));
    fixture.detectChanges();

    const emailWithRisk: EmailSummary = {
      id: 42,
      gmailId: 'g-42',
      sender: 'x@y.com',
      senderName: 'X Y',
      subject: 'Test',
      snippet: 'Snippet',
      receivedAt: '2026-07-01T10:00:00Z',
      fetchedAt: '2026-07-01T10:05:00Z',
      isRead: false,
      originalDateHeader: null,
      isImportant: false,
      isHidden: false,
      isDeleted: false,
      riskLevel: 'RED',
      riskPercentage: 85,
    };

    callBootstrap(makeDetail(42), emailWithRisk);

    expect(component.analysisState()).toBe('loading');

    const httpMock = TestBed.inject(HttpTestingController);
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/analysis');
    req.flush(makeCachedAnalysis(42));

    tick();
    fixture.detectChanges();

    expect(component.analysisState()).toBe('ready');
    expect(component.analysisResult()).not.toBeNull();
    expect(component.analysisResult()!.riskPercentage).toBe(85);

    for (const r of httpMock.match(() => true)) r.flush(emptyResponse());
  }));

  it('TRIAL + summary.riskLevel != null + getLatest returns 404 â†’ state idle', fakeAsync(() => {
    auth.currentUser.set(makeUser({ role: 'TRIAL' }));
    fixture.detectChanges();

    const emailWithRisk: EmailSummary = {
      id: 99,
      gmailId: 'g-99',
      sender: 'x@y.com',
      senderName: 'X Y',
      subject: 'Test',
      snippet: 'Snippet',
      receivedAt: '2026-07-01T10:00:00Z',
      fetchedAt: '2026-07-01T10:05:00Z',
      isRead: false,
      originalDateHeader: null,
      isImportant: false,
      isHidden: false,
      isDeleted: false,
      riskLevel: 'YELLOW',
      riskPercentage: 55,
    };

    callBootstrap(makeDetail(99), emailWithRisk);

    expect(component.analysisState()).toBe('loading');

    const httpMock = TestBed.inject(HttpTestingController);
    const req = httpMock.expectOne((r) => r.url === '/api/emails/99/analysis');
    req.flush(null, { status: 404, statusText: 'Not Found' });

    tick();
    fixture.detectChanges();

    expect(component.analysisState()).toBe('idle');
    expect(component.analysisResult()).toBeNull();

    for (const r of httpMock.match(() => true)) r.flush(emptyResponse());
  }));

  it('TRIAL + summary.riskLevel == null â†’ getLatest NOT called, state idle', fakeAsync(() => {
    auth.currentUser.set(makeUser({ role: 'TRIAL' }));
    fixture.detectChanges();

    const emailNoRisk: EmailSummary = {
      id: 7,
      gmailId: 'g-7',
      sender: 'x@y.com',
      senderName: 'X Y',
      subject: 'Normal email',
      snippet: 'Hello',
      receivedAt: '2026-07-01T10:00:00Z',
      fetchedAt: '2026-07-01T10:05:00Z',
      isRead: false,
      originalDateHeader: null,
      isImportant: false,
      isHidden: false,
      isDeleted: false,
    };

    const httpMock = TestBed.inject(HttpTestingController);

    callBootstrap(makeDetail(7), emailNoRisk);
    tick();
    fixture.detectChanges();

    const pending = httpMock.match((r) => r.url.includes('/analysis'));
    expect(pending.length).toBe(0);
    expect(component.analysisState()).toBe('idle');
    expect(component.analysisResult()).toBeNull();

    for (const r of httpMock.match(() => true)) r.flush(emptyResponse());
  }));
});
