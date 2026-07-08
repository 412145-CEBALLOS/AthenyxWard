import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmailViewerComponent } from './email-viewer';
import { EmailDetail } from '../../models/email-summary.model';
import { EmailAnalysisResult } from '../../models/email-analysis.model';
import { AiExplanation, AiState } from '../../models/ai-explanation.model';

function makeDetail(overrides: Partial<EmailDetail> = {}): EmailDetail {
  return {
    id: 1,
    gmailId: 'gid1',
    sender: 'test@example.com',
    senderName: 'Test User',
    subject: 'Test Subject',
    snippet: 'Snippet',
    contentForAnalysis: 'Body',
    htmlContent: null,
    receivedAt: '2026-07-01T10:00:00Z',
    fetchedAt: '2026-07-01T10:00:00Z',
    isRead: false,
    originalDateHeader: null,
    isImportant: false,
    isHidden: false,
    isDeleted: false,
    ...overrides,
  };
}

function makeAnalysis(overrides: Partial<EmailAnalysisResult> = {}): EmailAnalysisResult {
  return {
    analysisId: 1,
    emailId: 1,
    riskPercentage: 65,
    riskLevel: 'YELLOW',
    threatCategories: [],
    heuristicFindings: [],
    suspiciousUrls: [],
    senderTrust: {
      sender: 'test@example.com',
      displayName: 'Test User',
      domain: 'example.com',
      displayMismatch: false,
    },
    aiExplanation: 'Heuristic explanation',
    contentSummary: 'Summary',
    recommendedActions: [],
    analyzedAt: '2026-07-01T10:00:00Z',
    source: 'HEURISTIC',
    ...overrides,
  };
}

function makeAiExplanation(overrides: Partial<AiExplanation> = {}): AiExplanation {
  return {
    id: 1,
    text: 'Este correo parece ser un intento de phishing.',
    origin: 'LLM',
    modelName: 'llama3',
    generatedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    ...overrides,
  };
}

describe('EmailViewerComponent', () => {
  let component: EmailViewerComponent;
  let fixture: ComponentFixture<EmailViewerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailViewerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailViewerComponent);
    component = fixture.componentInstance;
  });

  function init(detail: Partial<EmailDetail> = {}, aiState: AiState = 'idle'): void {
    fixture.componentRef.setInput('email', makeDetail(detail));
    fixture.componentRef.setInput('aiState', aiState);
    fixture.detectChanges();
  }

  it('should create', () => {
    init();
    expect(component).toBeTruthy();
  });

  it('does not render AI loading when aiState is idle', () => {
    init({}, 'idle');
    const loading = fixture.nativeElement.querySelector('.ai-loading');
    expect(loading).toBeFalsy();
  });

  it('renders AI loading spinner when aiState is loading', () => {
    init({}, 'loading');
    const loading = fixture.nativeElement.querySelector('.ai-loading');
    expect(loading).toBeTruthy();
    expect(loading.textContent).toContain('Generando explicación con IA…');
  });

  it('does not render AI card when aiState is loading', () => {
    init({}, 'loading');
    const card = fixture.nativeElement.querySelector('.ai-card');
    expect(card).toBeFalsy();
  });

  it('renders AI card with badge and text when aiState is ready', () => {
    fixture.componentRef.setInput('email', makeDetail());
    fixture.componentRef.setInput('aiExplanation', makeAiExplanation());
    fixture.componentRef.setInput('aiState', 'ready');
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.ai-card');
    expect(card).toBeTruthy();
    const badge = card.querySelector('.ai-badge');
    expect(badge.textContent).toContain('Generado por IA');
    const text = card.querySelector('.ai-text');
    expect(text.textContent).toContain('phishing');
  });

  it('renders LLM badge with --llm class', () => {
    fixture.componentRef.setInput('email', makeDetail());
    fixture.componentRef.setInput('aiExplanation', makeAiExplanation({ origin: 'LLM', modelName: 'llama3' }));
    fixture.componentRef.setInput('aiState', 'ready');
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.ai-badge');
    expect(badge.classList).toContain('ai-badge--llm');
  });

  it('renders FALLBACK badge with --fallback class', () => {
    fixture.componentRef.setInput('email', makeDetail());
    fixture.componentRef.setInput('aiExplanation', makeAiExplanation({ origin: 'FALLBACK', modelName: '' }));
    fixture.componentRef.setInput('aiState', 'ready');
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.ai-badge');
    expect(badge.classList).toContain('ai-badge--fallback');
    expect(badge.textContent).toContain('Explicación heurística');
  });

  it('shows model name when origin is LLM and modelName is set', () => {
    fixture.componentRef.setInput('email', makeDetail());
    fixture.componentRef.setInput('aiExplanation', makeAiExplanation({ origin: 'LLM', modelName: 'llama3' }));
    fixture.componentRef.setInput('aiState', 'ready');
    fixture.detectChanges();

    const model = fixture.nativeElement.querySelector('.ai-model');
    expect(model).toBeTruthy();
    expect(model.textContent).toContain('llama3');
  });

  it('does not show model name when origin is FALLBACK', () => {
    fixture.componentRef.setInput('email', makeDetail());
    fixture.componentRef.setInput('aiExplanation', makeAiExplanation({ origin: 'FALLBACK', modelName: '' }));
    fixture.componentRef.setInput('aiState', 'ready');
    fixture.detectChanges();

    const model = fixture.nativeElement.querySelector('.ai-model');
    expect(model).toBeFalsy();
  });

  it('emits explainRequest when header AI button is clicked', fakeAsync(() => {
    const email = makeDetail();
    fixture.componentRef.setInput('email', email);
    fixture.detectChanges();
    let emitted = false;
    component.explainRequest.subscribe(() => (emitted = true));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    btn.click();
    tick();
    expect(emitted).toBeTrue();
  }));

  it('relativeTime returns Spanish string', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    const result = component.relativeTime(fiveMinAgo);
    expect(result).toMatch(/hace \d+ min/);
  });
});
