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
    isHidden: false,
    isDeleted: false,
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

  it('renders 4 kebab items', () => {
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items.length).toBe(4);
  });

  it('emits explainRequest when AI button is clicked', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    let emitted = false;
    component.explainRequest.subscribe(() => (emitted = true));
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    btn.click();
    expect(emitted).toBeTrue();
  });

  it('AI button is disabled when canExplain=false with tooltip', () => {
    fixture.componentRef.setInput('canExplain', false);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    expect(btn.title).toBe('Analiza primero el correo');
  });

  it('AI button is disabled when aiEnabled=false with tooltip', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('aiEnabled', false);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    expect(btn.title).toBe('La IA está deshabilitada por el administrador');
  });

  it('AI button is enabled when aiEnabled=true and other conditions met', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('aiEnabled', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeFalse();
    expect(btn.title).toBe('');
  });

  it('AI button is disabled when isDeleted=true with tooltip', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('isDeleted', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    expect(btn.title).toBe('No disponible para correos eliminados');
  });

  it('AI button is enabled when canExplain=true and not deleted', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeFalse();
    expect(btn.title).toBe('');
  });

  it('TRIAL: AI button is not rendered', () => {
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.componentRef.setInput('canExplain', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn');
    expect(btn).toBeNull();
  });

  it('PREMIUM: AI button is rendered', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('canExplain', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.ai-explain-btn') as HTMLButtonElement;
    expect(btn).not.toBeNull();
    expect(btn.disabled).toBeFalse();
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
    allItems[2].click();
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
    allItems[3].click();
    expect(emitted as unknown as string).toBe('delete');
  });

  it('TRIAL: mark-important and create-reminder are disabled with tooltip', () => {
    fixture.componentRef.setInput('userRole', 'TRIAL');
    fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items[0].getAttribute('aria-disabled')).toBe('true');
    expect(items[0].getAttribute('title')).toContain('Premium');
    expect(items[1].getAttribute('aria-disabled')).toBe('true');
    expect(items[1].getAttribute('title')).toContain('Premium');
    expect(items[2].getAttribute('aria-disabled')).toBeNull();
    expect(items[3].getAttribute('aria-disabled')).toBeNull();
  });

  it('PREMIUM: all items enabled except TRIAL restrictions', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector('.kebab-trigger');
    trigger.click();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items[0].getAttribute('aria-disabled')).toBeNull();
    expect(items[1].getAttribute('aria-disabled')).toBeNull();
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
    expect(items[1].disabled).toBeTrue();
    expect(items[1].disabledTooltip).toContain('pendiente');
  });

  it('isHidden=true changes hide label to "Mostrar correo"', () => {
    fixture.componentRef.setInput('isHidden', true);
    fixture.detectChanges();
    expect(component.hideLabel()).toBe('Mostrar correo');
    const hideItem = component.items().find((i) => i.id === 'hide');
    expect(hideItem?.label).toBe('Mostrar correo');
  });

  it('delete item has variant destructive', () => {
    const items = component.items();
    expect(items[3].variant).toBe('destructive');
  });

  it('isDeleted=true disables all kebab items', () => {
    fixture.componentRef.setInput('userRole', 'PREMIUM');
    fixture.componentRef.setInput('isDeleted', true);
    fixture.detectChanges();
    const items = component.items();
    expect(items[0].disabled).toBeTrue();
    expect(items[0].disabledTooltip).toContain('eliminados');
    expect(items[1].disabled).toBeTrue();
    expect(items[1].disabledTooltip).toContain('eliminados');
    expect(items[2].disabled).toBeTrue();
    expect(items[2].disabledTooltip).toContain('eliminados');
    expect(items[3].disabled).toBeTrue();
    expect(items[3].disabledTooltip).toContain('ya fue eliminado');
  });
});
