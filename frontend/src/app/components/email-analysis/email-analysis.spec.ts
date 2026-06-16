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
});
