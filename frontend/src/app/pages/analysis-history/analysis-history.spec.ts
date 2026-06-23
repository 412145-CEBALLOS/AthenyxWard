import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AnalysisHistoryComponent } from './analysis-history';
import { AnalysisHistoryItem } from '../../models/email-analysis.model';

function makeItem(overrides: Partial<AnalysisHistoryItem> = {}): AnalysisHistoryItem {
  return {
    analysisId: 1,
    emailId: 10,
    sender: 'sender@example.com',
    subject: 'Test subject',
    riskPercentage: 50,
    riskLevel: 'YELLOW',
    analyzedAt: '2026-06-08T10:00:00Z',
    summary: 'Test summary',
    ...overrides,
  };
}

describe('AnalysisHistoryComponent', () => {
  let component: AnalysisHistoryComponent;
  let fixture: ComponentFixture<AnalysisHistoryComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisHistoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalysisHistoryComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
    expect(component).toBeTruthy();
  });

  it('renders loading spinner on init', () => {
    fixture.detectChanges();
    const loading = fixture.nativeElement.querySelector('.history-loading');
    expect(loading).toBeTruthy();
    expect(loading.textContent).toContain('Cargando');

    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
  });

  it('renders items on success', () => {
    const items = [makeItem({ analysisId: 1, sender: 'a@b.com', riskLevel: 'RED', riskPercentage: 87 })];
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items, currentPage: 0, totalPages: 1, totalItems: 1 });
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.history-card');
    expect(cards.length).toBe(1);
    expect(cards[0].getAttribute('data-level')).toBe('RED');
    expect(cards[0].textContent).toContain('a@b.com');
    expect(cards[0].textContent).toContain('Peligroso');
    expect(cards[0].textContent).toContain('87%');
  });

  it('renders error state on 500', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.history-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('No se pudo cargar el historial');
  });

  it('renders empty state when totalItems is 0 and no filters', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.history-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('Aún no has analizado');
  });

  it('sends default page=0 and size=20', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('to')).toBe(false);
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
  });

  it('sends date filters when applied', () => {
    component.from.set('2026-06-01');
    component.to.set('2026-06-30');
    component.applyFilters();

    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    expect(req.request.params.get('from')).toBe('2026-06-01');
    expect(req.request.params.get('to')).toBe('2026-06-30');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
  });

  it('resets page to 0 when applying filters', () => {
    component.currentPage.set(2);
    component.from.set('2026-06-01');
    component.applyFilters();

    expect(component.currentPage()).toBe(0);
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
  });

  it('clearFilters wipes dates and refetches', () => {
    component.from.set('2026-06-01');
    component.to.set('2026-06-30');
    component.clearFilters();

    expect(component.from()).toBeNull();
    expect(component.to()).toBeNull();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('to')).toBe(false);
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
  });

  it('paginator triggers a new fetch with the next page number', () => {
    const items = [makeItem({ analysisId: 1 })];
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items, currentPage: 0, totalPages: 3, totalItems: 45 });
    fixture.detectChanges();

    component.onPageChange(1);

    const req2 = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    expect(req2.request.params.get('page')).toBe('1');
    req2.flush({ items, currentPage: 1, totalPages: 3, totalItems: 45 });
  });

  it('shows the filtered-empty state when filters return no items', () => {
    fixture.detectChanges();
    const initReq = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    initReq.flush({ items: [makeItem()], currentPage: 0, totalPages: 1, totalItems: 1 });
    fixture.detectChanges();

    component.from.set('2026-01-01');
    component.to.set('2026-01-31');
    component.applyFilters();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/analysis/history' &&
        r.params.get('from') === '2026-01-01',
    );
    req.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.history-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('No hay análisis en este rango');
  });

  it('hasNextPage is true when currentPage is less than totalPages-1', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 0, totalPages: 3, totalItems: 45 });
    fixture.detectChanges();
    expect(component.hasNextPage()).toBe(true);
  });

  it('hasNextPage is false on the last page', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    req.flush({ items: [], currentPage: 2, totalPages: 3, totalItems: 45 });
    fixture.detectChanges();
    expect(component.hasNextPage()).toBe(false);
  });

  it('levelLabel returns Spanish labels for each risk level', () => {
    expect(component.levelLabel('GREEN')).toBe('Seguro');
    expect(component.levelLabel('YELLOW')).toBe('Sospechoso');
    expect(component.levelLabel('RED')).toBe('Peligroso');
  });

  it('clamps onPageChange to the last valid page when jumping beyond totalPages', () => {
    fixture.detectChanges();
    const initReq = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    initReq.flush({ items: [makeItem()], currentPage: 0, totalPages: 4, totalItems: 80 });
    fixture.detectChanges();

    component.onPageChange(10);

    expect(component.currentPage()).toBe(3);
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/analysis/history' && r.params.get('page') === '3',
    );
    req.flush({ items: [makeItem()], currentPage: 3, totalPages: 4, totalItems: 80 });
  });

  it('clamps onPageChange to 0 when negative page is requested', () => {
    fixture.detectChanges();
    const initReq = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    initReq.flush({ items: [makeItem()], currentPage: 2, totalPages: 4, totalItems: 80 });
    fixture.detectChanges();

    component.onPageChange(-2);

    expect(component.currentPage()).toBe(0);
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/analysis/history' && r.params.get('page') === '0',
    );
    req.flush({ items: [makeItem()], currentPage: 0, totalPages: 4, totalItems: 80 });
  });

  it('does not refetch when onPageChange is called with the current page', () => {
    fixture.detectChanges();
    const initReq = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    initReq.flush({ items: [makeItem()], currentPage: 1, totalPages: 4, totalItems: 80 });
    fixture.detectChanges();

    component.onPageChange(1);

    httpMock.expectNone(() => true);
  });

  it('does not show the "no analyses" empty state when totalItems > 0 but current page is empty', () => {
    fixture.detectChanges();
    const initReq = httpMock.expectOne((r) => r.url === '/api/analysis/history');
    initReq.flush({ items: [], currentPage: 5, totalPages: 4, totalItems: 80 });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.history-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).not.toContain('Aún no has analizado');
  });
});
