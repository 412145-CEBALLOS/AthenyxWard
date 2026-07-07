import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmailItemComponent } from './email-item';
import { EmailSummary } from '../../models/email-summary.model';

const buildEmail = (overrides: Partial<EmailSummary> = {}): EmailSummary => ({
  id: 1,
  gmailId: 'gid-1',
  sender: 'user@example.com',
  senderName: 'User',
  subject: 'Subject',
  snippet: 'Snippet',
  receivedAt: '2026-06-22T12:00:00Z',
  fetchedAt: '2026-06-22T12:00:00Z',
  isRead: true,
  originalDateHeader: null,
  isImportant: false,
  isHidden: false,
  ...overrides,
});

describe('EmailItemComponent', () => {
  let fixture: ComponentFixture<EmailItemComponent>;

  const setInputs = (overrides: {
    email?: EmailSummary;
    accessibilityMode?: boolean;
    selected?: boolean;
  }): void => {
    fixture.componentRef.setInput('email', overrides.email ?? buildEmail());
    if (overrides.accessibilityMode !== undefined) {
      fixture.componentRef.setInput('accessibilityMode', overrides.accessibilityMode);
    }
    if (overrides.selected !== undefined) {
      fixture.componentRef.setInput('selected', overrides.selected);
    }
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailItemComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailItemComponent);
  });

  it('should create', () => {
    setInputs({});
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders no risk indicator when riskLevel is null', () => {
    setInputs({ email: buildEmail({ riskLevel: null, riskPercentage: null }) });
    const li = fixture.nativeElement.querySelector('li');
    expect(li.classList.contains('risk-yellow')).toBeFalse();
    expect(li.classList.contains('risk-red')).toBeFalse();
    expect(fixture.nativeElement.querySelector('.risk-dot')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg')).toBeNull();
  });

  it('accessibility ON (default) + GREEN: no dot, no background', () => {
    // Per US 2.3 spec, GREEN keeps the row clean in accessibility
    // mode — no dot is shown.
    setInputs({ email: buildEmail({ riskLevel: 'GREEN', riskPercentage: 10 }) });
    const li = fixture.nativeElement.querySelector('li');
    expect(li.classList.contains('risk-yellow')).toBeFalse();
    expect(li.classList.contains('risk-red')).toBeFalse();
    expect(fixture.nativeElement.querySelector('.risk-dot')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg')).toBeNull();
  });

  it('accessibility OFF + GREEN: renders subtle green background, no dot', () => {
    // Extension: in non-accessibility mode, analysed safe emails
    // also get a (subtle) colour cue so the user can distinguish
    // "GREEN analysed" from "not yet analysed".
    setInputs({
      email: buildEmail({ riskLevel: 'GREEN', riskPercentage: 10 }),
      accessibilityMode: false,
    });
    const bg = fixture.nativeElement.querySelector('.risk-bg.risk-bg-green');
    expect(bg).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.risk-dot')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg-yellow')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg-red')).toBeNull();
  });

  it('accessibility ON + YELLOW: renders yellow dot, no background', () => {
    setInputs({
      email: buildEmail({ riskLevel: 'YELLOW', riskPercentage: 55 }),
      accessibilityMode: true,
    });
    const dot = fixture.nativeElement.querySelector('.risk-dot.risk-dot-yellow');
    expect(dot).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.risk-bg')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-dot-red')).toBeNull();
  });

  it('accessibility ON + RED: renders red dot, no background', () => {
    setInputs({
      email: buildEmail({ riskLevel: 'RED', riskPercentage: 88 }),
      accessibilityMode: true,
    });
    const dot = fixture.nativeElement.querySelector('.risk-dot.risk-dot-red');
    expect(dot).toBeTruthy();
    expect(dot.getAttribute('aria-label')).toBe('88% riesgo');
    expect(fixture.nativeElement.querySelector('.risk-bg')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-dot-yellow')).toBeNull();
  });

  it('accessibility OFF + YELLOW: renders background layer, no dot', () => {
    setInputs({
      email: buildEmail({ riskLevel: 'YELLOW', riskPercentage: 55 }),
      accessibilityMode: false,
    });
    const bg = fixture.nativeElement.querySelector('.risk-bg.risk-bg-yellow');
    expect(bg).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.risk-dot')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg-red')).toBeNull();
  });

  it('accessibility OFF + RED: renders red background layer, no dot', () => {
    setInputs({
      email: buildEmail({ riskLevel: 'RED', riskPercentage: 88 }),
      accessibilityMode: false,
    });
    const bg = fixture.nativeElement.querySelector('.risk-bg.risk-bg-red');
    expect(bg).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.risk-dot')).toBeNull();
    expect(fixture.nativeElement.querySelector('.risk-bg-yellow')).toBeNull();
  });

  it('ARIA label of the dot reflects the risk percentage', () => {
    setInputs({
      email: buildEmail({ riskLevel: 'YELLOW', riskPercentage: 42 }),
      accessibilityMode: true,
    });
    const dot = fixture.nativeElement.querySelector('.risk-dot');
    expect(dot.getAttribute('aria-label')).toBe('42% riesgo');
  });
});
