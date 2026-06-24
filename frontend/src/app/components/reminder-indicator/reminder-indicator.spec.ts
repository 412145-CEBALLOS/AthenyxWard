import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ReminderAction,
  ReminderIndicatorComponent,
} from './reminder-indicator';
import { Reminder, ReminderSummary } from '../../models/reminder.model';

const buildSummary = (overrides: Partial<ReminderSummary> = {}): ReminderSummary => ({
  id: 1,
  reminderDate: new Date(Date.now() + 60 * 60_000).toISOString(),
  done: false,
  ...overrides,
});

const buildReminder = (overrides: Partial<Reminder> = {}): Reminder => ({
  id: 1,
  emailId: 10,
  reminderDate: new Date(Date.now() + 60 * 60_000).toISOString(),
  message: 'Llamar al banco',
  done: false,
  createdAt: '2026-06-22T09:00:00Z',
  updatedAt: '2026-06-22T09:00:00Z',
  ...overrides,
});

describe('ReminderIndicatorComponent', () => {
  let fixture: ComponentFixture<ReminderIndicatorComponent>;

  const setInputs = (overrides: {
    reminder?: Reminder | ReminderSummary | null;
    variant?: 'list' | 'banner';
  }): void => {
    fixture.componentRef.setInput('reminder', overrides.reminder ?? null);
    if (overrides.variant) {
      fixture.componentRef.setInput('variant', overrides.variant);
    }
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReminderIndicatorComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(ReminderIndicatorComponent);
  });

  it('renders nothing when no reminder is provided', () => {
    setInputs({ reminder: null, variant: 'list' });
    expect(fixture.nativeElement.querySelector('.reminder-chip-list')).toBeNull();
    expect(fixture.nativeElement.querySelector('.reminder-banner')).toBeNull();
  });

  it('renders a bell chip in list variant when an active reminder exists', () => {
    setInputs({ reminder: buildSummary(), variant: 'list' });
    const chip = fixture.nativeElement.querySelector('.reminder-chip-list');
    expect(chip).toBeTruthy();
    expect(chip.getAttribute('aria-label')).toContain('Recordatorio');
  });

  it('renders the chip with the done class for completed reminders', () => {
    setInputs({ reminder: buildSummary({ done: true }), variant: 'list' });
    const chip = fixture.nativeElement.querySelector('.reminder-chip-list');
    expect(chip.classList.contains('reminder-done')).toBeTrue();
  });

  it('does not render banner in list variant', () => {
    setInputs({ reminder: buildSummary(), variant: 'list' });
    expect(fixture.nativeElement.querySelector('.reminder-banner')).toBeNull();
  });

  it('renders a banner with date, message, and action buttons in banner variant', () => {
    setInputs({ reminder: buildReminder(), variant: 'banner' });
    const banner = fixture.nativeElement.querySelector('.reminder-banner');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Recordatorio');
    expect(banner.textContent).toContain('Llamar al banco');
    const buttons = fixture.nativeElement.querySelectorAll('.reminder-btn');
    expect(buttons.length).toBeGreaterThanOrEqual(2);
  });

  it('renders the banner with the done class for completed reminders', () => {
    setInputs({ reminder: buildReminder({ done: true }), variant: 'banner' });
    const banner = fixture.nativeElement.querySelector('.reminder-banner');
    expect(banner.classList.contains('reminder-done')).toBeTrue();
  });

  it('omits the "Marcar hecho" button for completed reminders', () => {
    setInputs({ reminder: buildReminder({ done: true }), variant: 'banner' });
    const primaryBtn = fixture.nativeElement.querySelector('.reminder-btn-primary');
    expect(primaryBtn).toBeNull();
  });

  it('marks an active reminder as upcoming when within 24h', () => {
    const soon = new Date(Date.now() + 2 * 60 * 60_000).toISOString();
    setInputs({ reminder: buildSummary({ reminderDate: soon }), variant: 'list' });
    const chip = fixture.nativeElement.querySelector('.reminder-chip-list');
    expect(chip.classList.contains('reminder-upcoming')).toBeTrue();
  });

  it('does NOT mark a reminder as upcoming when > 24h away', () => {
    const later = new Date(Date.now() + 48 * 60 * 60_000).toISOString();
    setInputs({ reminder: buildSummary({ reminderDate: later }), variant: 'list' });
    const chip = fixture.nativeElement.querySelector('.reminder-chip-list');
    expect(chip.classList.contains('reminder-upcoming')).toBeFalse();
  });

  it('emits a view action when the list chip is clicked', () => {
    setInputs({ reminder: buildSummary(), variant: 'list' });
    const emitted: ReminderAction[] = [];
    fixture.componentRef.instance.action.subscribe((a) => emitted.push(a));
    const chip = fixture.nativeElement.querySelector('.reminder-chip-list');
    chip.click();
    expect(emitted.length).toBe(1);
    expect(emitted[0].type).toBe('view');
  });

  it('emits markDone when the primary action fires mousedown (US 2.6: avoids pressed-cursor lock)', () => {
    setInputs({ reminder: buildReminder(), variant: 'banner' });
    const emitted: ReminderAction[] = [];
    fixture.componentRef.instance.action.subscribe((a) => emitted.push(a));
    const primaryBtn = fixture.nativeElement.querySelector('.reminder-btn-primary') as HTMLButtonElement;
    // The template binds (mousedown), not (click) — this is the
    // regression guard for the freeze where the click sequence
    // left the browser in a pressed-cursor state and re-rendered
    // the row out from under the user's mouse.
    primaryBtn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
    expect(emitted.length).toBe(1);
    expect(emitted[0].type).toBe('markDone');
  });

  it('emits delete when the trash button is clicked', () => {
    setInputs({ reminder: buildReminder(), variant: 'banner' });
    const emitted: ReminderAction[] = [];
    fixture.componentRef.instance.action.subscribe((a) => emitted.push(a));
    const buttons = fixture.nativeElement.querySelectorAll('.reminder-btn');
    const trash = buttons[buttons.length - 1] as HTMLButtonElement;
    trash.click();
    expect(emitted.length).toBe(1);
    expect(emitted[0].type).toBe('delete');
  });
});
