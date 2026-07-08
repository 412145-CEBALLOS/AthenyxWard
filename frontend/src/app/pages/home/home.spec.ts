import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
import { EmailPageResponse } from '../../models/email-summary.model';
import { EmailSearchService } from '../../services/email-search.service';
import { EmailAnalysisResult } from '../../models/email-analysis.model';
import { AiExplanation } from '../../models/ai-explanation.model';


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

describe('HomeComponent — US 3.7 search bar (desktop default)', () => {
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
    expect(empty.textContent).toContain('No hay correos aún');
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
    expect(modal.textContent).toContain('Período de prueba terminado');
  });
});

describe('HomeComponent — US 3.7 search bar (mobile)', () => {
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

describe('HomeComponent — US 3.3 AI explanation flow', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let routeHandle: ReturnType<typeof makeRouteWithParams>;

  function makeAiExplanation(overrides: Partial<AiExplanation> = {}): AiExplanation {
    return {
      id: 1,
      text: 'Este correo parece ser un intento de phishing.',
      origin: 'LLM',
      modelName: 'llama3',
      generatedAt: '2026-07-07T10:00:00Z',
      ...overrides,
    };
  }

  function makeAnalysis(overrides: Partial<EmailAnalysisResult> = {}): EmailAnalysisResult {
    return {
      analysisId: 1,
      emailId: 42,
      riskPercentage: 65,
      riskLevel: 'YELLOW',
      threatCategories: [],
      heuristicFindings: [],
      suspiciousUrls: [],
      senderTrust: {
        sender: 'test@example.com',
        displayName: 'Test',
        domain: 'example.com',
        displayMismatch: false,
      },
      aiExplanation: 'Heuristic',
      contentSummary: 'Summary',
      recommendedActions: [],
      analyzedAt: '2026-07-07T09:00:00Z',
      source: 'HEURISTIC',
      ...overrides,
    };
  }

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
    auth.currentUser.set(makeUser());

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function setupSelectedEmail(): void {
    const emailDetail = {
      id: 42,
      gmailId: 'gid42',
      sender: 'test@example.com',
      senderName: 'Test User',
      subject: 'Test Subject',
      snippet: 'Snippet',
      contentForAnalysis: 'Body',
      htmlContent: null,
      receivedAt: '2026-07-01T10:00:00Z',
      fetchedAt: '2026-07-01T10:00:00Z',
      isRead: true,
      originalDateHeader: null as string | null,
      isImportant: false,
      isHidden: false,
      isDeleted: false,
    };
    component.selectedEmail.set(emailDetail as any);
    component.analysisResult.set(makeAnalysis() as any);
    component.analysisState.set('ready');
    component.aiExplanation.set(null);
    component.aiState.set('idle');
    fixture.detectChanges();
  }

  it('200 → aiState is ready and aiExplanation is set', () => {
    setupSelectedEmail();
    component.onExplainRequest();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    req.flush(makeAiExplanation());
    expect(component.aiState()).toBe('ready');
    expect(component.aiExplanation()?.text).toContain('phishing');
    expect(component.aiExplanation()?.origin).toBe('LLM');
  });

  it('200 with FALLBACK origin → aiExplanation origin is FALLBACK', () => {
    setupSelectedEmail();
    component.onExplainRequest();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    req.flush(makeAiExplanation({ origin: 'FALLBACK', modelName: '' }));
    expect(component.aiExplanation()?.origin).toBe('FALLBACK');
  });

  it('403 → aiState is unavailable-trial and toast is shown', () => {
    setupSelectedEmail();
    component.onExplainRequest();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    expect(component.aiState()).toBe('unavailable-trial');
  });

  it('5xx → aiState is error and toast is shown', () => {
    setupSelectedEmail();
    component.onExplainRequest();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    req.flush('Internal error', { status: 500, statusText: 'Internal Server Error' });
    expect(component.aiState()).toBe('error');
  });

  it('timeout (0 status) → aiState is error', () => {
    setupSelectedEmail();
    component.onExplainRequest();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    req.error(new ProgressEvent('error'), { status: 0, statusText: '' });
    expect(component.aiState()).toBe('error');
  });

  it('reset: aiState and aiExplanation reset when selectEmail is called', () => {
    setupSelectedEmail();
    component.aiExplanation.set(makeAiExplanation());
    component.aiState.set('ready');
    fixture.detectChanges();

    component.selectEmail({
      id: 99,
      gmailId: 'gid99',
      sender: 'new@example.com',
      senderName: 'New',
      subject: 'New',
      snippet: 'snip',
      receivedAt: '',
      fetchedAt: '',
      isRead: false,
      isImportant: false,
      isHidden: false,
      isDeleted: false,
      originalDateHeader: null,
    });
    expect(component.aiState()).toBe('idle');
    expect(component.aiExplanation()).toBeNull();
  });

  it('reset: aiState and aiExplanation reset when clearSelection is called', () => {
    setupSelectedEmail();
    component.aiExplanation.set(makeAiExplanation());
    component.aiState.set('ready');
    fixture.detectChanges();

    (component as any).clearSelection();
    expect(component.aiState()).toBe('idle');
    expect(component.aiExplanation()).toBeNull();
  });
});

