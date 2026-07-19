import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuditDetailDrawerComponent } from './audit-detail-drawer';
import { AuditEntryResponse } from '../../models/audit.model';

describe('AuditDetailDrawerComponent', () => {
  let component: AuditDetailDrawerComponent;
  let fixture: ComponentFixture<AuditDetailDrawerComponent>;

  const mockEntry: AuditEntryResponse = {
    id: 1,
    createdAt: '2026-07-13T10:00:00',
    actorId: 1,
    actorEmail: 'u@test.com',
    actorRole: 'ADMIN',
    actionType: 'LOGIN',
    targetType: 'SESSION',
    targetId: 'session-1',
    severity: 'INFO',
    result: 'SUCCESS',
    payload: '{"key":"value"}',
    ipAddress: '192.168.1.1',
    userAgent: 'TestBrowser',
    correlationId: 'corr-123',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditDetailDrawerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditDetailDrawerComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('is hidden when open is false', () => {
    fixture.componentRef.setInput('open', false);
    fixture.componentRef.setInput('entry', mockEntry);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.drawer-overlay')).toBeNull();
  });

  it('shows overlay when open is true', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('entry', mockEntry);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.drawer-overlay')).not.toBeNull();
    expect(el.querySelector('.drawer-panel')).not.toBeNull();
  });

  it('emits closeDrawer when overlay is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('entry', mockEntry);
    fixture.detectChanges();

    let emitted = false;
    component.closeDrawer.subscribe(() => (emitted = true));
    component.onOverlayClick();
    expect(emitted).toBeTrue();
  });

  it('parses JSON payload', () => {
    const parsed = component.parsePayload('{"key":"value"}');
    expect(parsed).toEqual({ key: 'value' });
  });

  it('returns empty object for null payload', () => {
    const parsed = component.parsePayload(null);
    expect(parsed).toEqual({});
  });

  it('returns empty object for invalid JSON', () => {
    const parsed = component.parsePayload('not-json');
    expect(parsed).toEqual({});
  });
});
