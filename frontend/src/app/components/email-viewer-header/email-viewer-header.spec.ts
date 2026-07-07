import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmailViewerHeaderComponent } from './email-viewer-header';
import { EmailDetail } from '../../models/email-summary.model';

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
    ...overrides,
  };
}

describe('EmailViewerHeaderComponent', () => {
  let component: EmailViewerHeaderComponent;
  let fixture: ComponentFixture<EmailViewerHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailViewerHeaderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailViewerHeaderComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('email', makeDetail());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders 5 kebab items', () => {
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items.length).toBe(5);
  });

  it('emits explain-ai action', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[role="menuitem"]').click();
    expect(emitted as unknown as string).toBe('explain-ai');
  });

  it('emits hide action', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const allItems = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    allItems[3].click();
    expect(emitted as unknown as string).toBe('hide');
  });

  it('emits delete action', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    let emitted: string | null = null;
    component.action.subscribe((id) => (emitted = id));
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const allItems = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    allItems[4].click();
    expect(emitted as unknown as string).toBe('delete');
  });

  it('TRIAL: mark-important and create-reminder are disabled with tooltip', () => {
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items[1].getAttribute('aria-disabled')).toBe('true');
    expect(items[1].getAttribute('title')).toContain('Premium');
    expect(items[2].getAttribute('aria-disabled')).toBe('true');
    expect(items[2].getAttribute('title')).toContain('Premium');
    expect(items[0].getAttribute('aria-disabled')).toBeNull();
    expect(items[3].getAttribute('aria-disabled')).toBeNull();
    expect(items[4].getAttribute('aria-disabled')).toBeNull();
  });

  it('PREMIUM: all items enabled except TRIAL restrictions', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items[1].getAttribute('aria-disabled')).toBeNull();
    expect(items[2].getAttribute('aria-disabled')).toBeNull();
  });

  it('isImportant=true changes label to "Quitar importante" and sets active on mark-important item', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('isImportant', true);
    fixture.detectChanges();
    expect(component.importantLabel()).toBe('Quitar importante');
    const markImportantItem = component.items().find((i) => i.id === 'mark-important');
    expect(markImportantItem?.active).toBeTrue();
  });

  it('hasReminder=true and not pending changes label to "Reactivar recordatorio"', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('hasReminder', true);
    fixture.componentRef.setInput('hasPendingReminder', false);
    fixture.detectChanges();
    expect(component.reminderLabel()).toBe('Reactivar recordatorio');
  });

  it('hasPendingReminder=true changes label to "Ver recordatorio" and disables item', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('hasPendingReminder', true);
    fixture.detectChanges();
    expect(component.reminderLabel()).toBe('Ver recordatorio');
    const items = component.items();
    expect(items[2].disabled).toBeTrue();
    expect(items[2].disabledTooltip).toContain('pendiente');
  });

  it('delete item has variant destructive', () => {
    const items = component.items();
    expect(items[4].variant).toBe('destructive');
  });
});
