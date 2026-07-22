import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LayoutComponent } from './layout';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { PopupService } from '../../services/popup.service';
import { signal, PLATFORM_ID, Signal, WritableSignal } from '@angular/core';
import { ChangeDetectionStrategy } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

class MockAppConfigInitializerService {
  readonly supportEmail: Signal<string> = signal('support@athenyxward.com').asReadonly();
  readonly pollIntervalSeconds: WritableSignal<number> = signal(120);
  readonly loading: Signal<boolean> = signal(false).asReadonly();
  load = jasmine.createSpy('load');
}

class MockAuthService {
  readonly currentUser = signal(null);
  readonly user = this.currentUser.asReadonly();
  checkAuth = jasmine.createSpy('checkAuth').and.returnValue(of(null));
}

class MockToastService {
  success = jasmine.createSpy('success');
  error = jasmine.createSpy('error');
  info = jasmine.createSpy('info');
  warning = jasmine.createSpy('warning');
}

class MockPopupService {
  closePopup = jasmine.createSpy('closePopup');
  setPopup = jasmine.createSpy('setPopup');
  getPopup = jasmine.createSpy('getPopup');
}

describe('LayoutComponent', () => {
  let component: LayoutComponent;
  let fixture: ComponentFixture<LayoutComponent>;
  let mockAppConfig: MockAppConfigInitializerService;
  let mockAuth: MockAuthService;
  let mockToast: MockToastService;
  let mockPopupService: MockPopupService;

  beforeEach(async () => {
    mockAppConfig = new MockAppConfigInitializerService();
    mockAuth = new MockAuthService();
    mockToast = new MockToastService();
    mockPopupService = new MockPopupService();

    await TestBed.configureTestingModule({
      imports: [LayoutComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: AppConfigInitializerService, useValue: mockAppConfig },
        { provide: AuthService, useValue: mockAuth },
        { provide: ToastService, useValue: mockToast },
        { provide: PopupService, useValue: mockPopupService },
      ],
    })
      .overrideComponent(LayoutComponent, {
        set: { changeDetection: ChangeDetectionStrategy.Default },
      })
      .compileComponents();

    fixture = TestBed.createComponent(LayoutComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('calls appConfig.load() on init', () => {
    component.ngOnInit();
    expect(mockAppConfig.load).toHaveBeenCalled();
  });

  describe('onMessage', () => {
    function makeMpReturnMessage(status: string, overrides?: Record<string, unknown>): MessageEvent {
      return {
        data: {
          type: 'mp-return',
          origin: window.location.origin,
          status,
          ...overrides,
        },
        origin: window.location.origin,
        source: null,
      } as MessageEvent;
    }

    it('ignores messages with unknown type', () => {
      const event = makeMpReturnMessage('approved', { type: 'other' });
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).not.toHaveBeenCalled();
    });

    it('ignores messages without type field', () => {
      const event = { data: { foo: 'bar' }, origin: window.location.origin, source: null } as MessageEvent;
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).not.toHaveBeenCalled();
    });

    it('ignores messages when origin does not match window.location.origin', () => {
      const event = {
        data: { type: 'mp-return', origin: window.location.origin, status: 'approved' },
        origin: 'http://malicious.com',
        source: null,
      } as MessageEvent;
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).not.toHaveBeenCalled();
    });

    it('handles status=approved_callsCheckAuthAndShowsSuccessToast', () => {
      const event = makeMpReturnMessage('approved', { paymentId: '123' });
      mockAuth.checkAuth.and.returnValue(of({ id: 1, name: 'Test', email: 't@t.com', pictureUrl: '', role: 'PREMIUM', trialEndDate: null, trialExpired: false, accessibilityMode: false, termsAcceptedAt: null, termsVersion: null }));
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).toHaveBeenCalled();
      expect(mockToast.success).toHaveBeenCalledWith('Pago completado. ¡Bienvenido a Premium!');
      expect(mockPopupService.closePopup).toHaveBeenCalled();
    });

    it('handles status=in_process_callsCheckAuthAndShowsInfoToast', () => {
      const event = makeMpReturnMessage('in_process');
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).toHaveBeenCalled();
      expect(mockToast.info).toHaveBeenCalledWith('Tu pago está siendo procesado.');
      expect(mockPopupService.closePopup).toHaveBeenCalled();
    });

    it('handles status=rejected_showsErrorToast', () => {
      const event = makeMpReturnMessage('rejected');
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockAuth.checkAuth).not.toHaveBeenCalled();
      expect(mockToast.error).toHaveBeenCalledWith('El pago no se completó. Intentá nuevamente.');
      expect(mockPopupService.closePopup).toHaveBeenCalled();
    });

    it('handles status=failure_showsErrorToast', () => {
      const event = makeMpReturnMessage('failure');
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockToast.error).toHaveBeenCalledWith('El pago no se completó. Intentá nuevamente.');
      expect(mockPopupService.closePopup).toHaveBeenCalled();
    });

    it('handles checkAuth error after approved_showsErrorToast', () => {
      const event = makeMpReturnMessage('approved');
      mockAuth.checkAuth.and.returnValue(throwError(() => new Error('Unauthorized')));
      expect(() => component.onMessage(event)).not.toThrow();
      expect(mockToast.error).toHaveBeenCalledWith('Pago procesado. Iniciá sesión para ver tu cuenta Premium.');
      expect(mockPopupService.closePopup).toHaveBeenCalled();
    });
  });
});
