import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmailAnalysisComponent } from './email-analysis';
import { EmailAnalysisResult } from '../../models/email-analysis.model';

const buildAnalysis = (
  riskPercentage: number,
  overrides: Partial<EmailAnalysisResult> = {},
): EmailAnalysisResult => ({
  analysisId: 1,
  emailId: 10,
  riskPercentage,
  riskLevel: riskPercentage < 40 ? 'GREEN' : riskPercentage < 70 ? 'YELLOW' : 'RED',
  threatCategories: ['PHISHING'],
  heuristicFindings: [
    { rule: 'urgent-language', description: 'Tono de urgencia artificial', score: 20 },
  ],
  suspiciousUrls: [
    { raw: 'http://banc0-verify.example/login', resolvedDomain: 'banc0-verify.example',
      reason: 'Dominio no oficial del banco' },
  ],
  senderTrust: {
    sender: 'no-reply@banc0-verify.example',
    displayName: 'Banco Nacional',
    domain: 'banc0-verify.example',
    displayMismatch: true,
    spf: 'FAIL',
    dkim: 'FAIL',
  },
  aiExplanation: 'Correo sospechoso que simula ser del banco.',
  contentSummary: 'Solicita verificar identidad bajo amenaza de cierre.',
  recommendedActions: [
    { label: 'No hacer clic en los enlaces' },
    { label: 'Contactar al banco por canales oficiales' },
  ],
  analyzedAt: '2026-06-05T10:00:00Z',
  source: 'HYBRID',
  modelName: 'llama3',
  ...overrides,
});

describe('EmailAnalysisComponent', () => {
  let component: EmailAnalysisComponent;
  let fixture: ComponentFixture<EmailAnalysisComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailAnalysisComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailAnalysisComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('applies risk-safe class for low risk', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(15));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.analysis-panel');
    expect(el.classList).toContain('risk-safe');
    expect(component.statusLabel()).toBe('Seguro');
  });

  it('applies risk-suspicious class for medium risk', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(55));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.analysis-panel');
    expect(el.classList).toContain('risk-suspicious');
    expect(component.statusLabel()).toBe('Sospechoso');
  });

  it('applies risk-dangerous class for high risk', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(88));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.analysis-panel');
    expect(el.classList).toContain('risk-dangerous');
    expect(component.statusLabel()).toBe('Peligroso');
  });

  it('renders loading state with spinner', () => {
    fixture.componentRef.setInput('state', 'loading');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.spinner')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Analizando');
  });

  it('renders error state with retry button', () => {
    fixture.componentRef.setInput('state', 'error');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button.btn-ghost');
    expect(btn).toBeTruthy();
    expect(btn.textContent.trim()).toBe('Reintentar');
  });

  it('renders trial-unavailable message', () => {
    fixture.componentRef.setInput('state', 'unavailable-trial');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('límite');
  });

  it('toggles aria-expanded on the toggle button', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.panel-toggle');
    expect(btn.getAttribute('aria-expanded')).toBe('false');
    btn.click();
    fixture.detectChanges();
    expect(btn.getAttribute('aria-expanded')).toBe('true');
    btn.click();
    fixture.detectChanges();
    expect(btn.getAttribute('aria-expanded')).toBe('false');
  });

  it('disables premium actions for trial users', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.componentRef.setInput('canMarkImportant', false);
    fixture.detectChanges();
    const premiumButtons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('.btn-action-premium');
    expect(premiumButtons.length).toBeGreaterThan(0);
    premiumButtons.forEach((b) => expect(b.disabled).toBe(true));
  });

  // --- US 2.3 panel-toggle + trial button + risk format ---

  it('starts closed (aria-expanded=false) by default', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.panel-toggle');
    expect(btn.getAttribute('aria-expanded')).toBe('false');
  });

  it('toggle in idle + PREMIUM emits analyzeRequest and does NOT open', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    let emitted = false;
    component.analyzeRequest.subscribe(() => (emitted = true));
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.panel-toggle');
    btn.click();
    fixture.detectChanges();
    expect(emitted).toBe(true);
    expect(btn.getAttribute('aria-expanded')).toBe('false');
  });

  it('toggle in idle + TRIAL opens the panel and does NOT emit', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.detectChanges();
    let emitted = false;
    component.analyzeRequest.subscribe(() => (emitted = true));
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.panel-toggle');
    btn.click();
    fixture.detectChanges();
    expect(emitted).toBe(false);
    expect(btn.getAttribute('aria-expanded')).toBe('true');
  });

  it('trial button is rendered only when state=idle and userRole=TRIAL', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.state-block .btn-primary',
    );
    expect(btn).toBeTruthy();
    expect(btn.textContent.trim()).toBe('Analizar este correo');
  });

  it('trial button emits analyzeRequest when clicked', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.detectChanges();
    let emitted = false;
    component.analyzeRequest.subscribe(() => (emitted = true));
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.state-block .btn-primary',
    );
    btn.click();
    expect(emitted).toBe(true);
  });

  it('does NOT render trial button for PREMIUM users in idle state', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.state-block .btn-primary');
    expect(btn).toBeNull();
  });

  it('shows "X% riesgo" format on the toggle when ready', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(65));
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    const pct = fixture.nativeElement.querySelector('.toggle-pct');
    expect(pct.textContent.trim()).toBe('65% riesgo');
  });

  it('shows "Sin analizar" badge when idle with no analysis', () => {
    fixture.componentRef.setInput('state', 'idle');
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge.textContent.trim()).toBe('Sin analizar');
  });

  it('shows "Analizando…" badge while loading', () => {
    fixture.componentRef.setInput('state', 'loading');
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge.textContent.trim()).toBe('Analizando…');
  });

  it('showAfterAnalysis() opens the panel from the parent', () => {
    fixture.componentRef.setInput('state', 'ready');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.panel-toggle')
      .getAttribute('aria-expanded')).toBe('false');
    component.showAfterAnalysis();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.panel-toggle')
      .getAttribute('aria-expanded')).toBe('true');
  });

  // --- Reminder button states (hasReminder / hasPendingReminder) ---

  it('renders "Crear recordatorio" by default and emits createReminder on click', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.componentRef.setInput('canMarkImportant', true);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.actions-bar .btn-action-premium:nth-last-child(1)'
    );
    expect(btn.textContent.trim()).toBe('Crear recordatorio');
    expect(btn.disabled).toBeFalse();
    let emitted = false;
    component.createReminder.subscribe(() => (emitted = true));
    btn.click();
    expect(emitted).toBeTrue();
  });

  it('disables the create-reminder button when hasPendingReminder is true', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.componentRef.setInput('canMarkImportant', true);
    fixture.componentRef.setInput('hasPendingReminder', true);
    fixture.componentRef.setInput('hasReminder', true);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.actions-bar .btn-action-premium:nth-last-child(1)'
    );
    expect(btn.disabled).toBeTrue();
    expect(btn.textContent.trim()).toBe('Ver recordatorio');
    expect(btn.getAttribute('title')).toContain('pendiente');
  });

  it('switches to "Reactivar recordatorio" when the reminder is done', () => {
    fixture.componentRef.setInput('analysis', buildAnalysis(20));
    fixture.componentRef.setInput('state', 'ready');
    fixture.componentRef.setInput('canMarkImportant', true);
    fixture.componentRef.setInput('hasPendingReminder', false);
    fixture.componentRef.setInput('hasReminder', true);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.actions-bar .btn-action-premium:nth-last-child(1)'
    );
    expect(btn.disabled).toBeFalse();
    expect(btn.textContent.trim()).toBe('Reactivar recordatorio');
    expect(btn.getAttribute('title')).toContain('Reactivar');
  });
});
