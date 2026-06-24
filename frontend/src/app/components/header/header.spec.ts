import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { of, throwError } from 'rxjs';
import { HeaderComponent } from './header';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { UserInfo } from '../../models/user-info.model';
import { UpcomingNotification } from '../../models/notification.model';

const buildNotification = (overrides: Partial<UpcomingNotification> = {}): UpcomingNotification => ({
  reminderId: 1,
  emailId: 10,
  emailSubject: 'Subject',
  emailSender: 'a@b.com',
  message: null,
  reminderDate: new Date(Date.now() + 3600_000).toISOString(),
  isOverdue: false,
  ...overrides,
});

class AuthServiceStub {
  // The header reads `authService.user` as a signal call, so the
  // stub must expose a callable. We use a getter that returns a
  // frozen user object — no need to keep the value reactive in
  // these unit tests.
  user = (): UserInfo => ({
    id: 1, name: 'U', email: 'u@example.com', pictureUrl: '',
    role: 'PREMIUM', trialEndDate: null, trialExpired: false, accessibilityMode: true,
  });
}

describe('HeaderComponent — bell markDone', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let component: HeaderComponent;
  let notificationService: {
    markDone: jasmine.Spy;
    notifications: () => UpcomingNotification[];
    startPolling: jasmine.Spy;
    stopPolling: jasmine.Spy;
  };
  let http: HttpTestingController;

  const buildModule = async (): Promise<void> => {
    notificationService = {
      markDone: jasmine.createSpy('markDone').and.returnValue(of(undefined)),
      notifications: () => [buildNotification()],
      startPolling: jasmine.createSpy('startPolling'),
      stopPolling: jasmine.createSpy('stopPolling'),
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: AuthService, useValue: new AuthServiceStub() },
        { provide: NotificationService, useValue: notificationService },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  afterEach(() => {
    if (http) http.verify();
  });

  it('renders the bell done button with a mousedown handler (not click)', async () => {
    await buildModule();
    // Open the panel
    component.notificationsOpen.set(true);
    fixture.detectChanges();
    const btn: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '.notifications-action'
    );
    expect(btn).toBeTruthy();
    // The template must use mousedown, not click. We assert this
    // by simulating mousedown and confirming the handler fired.
    // A pure click should NOT fire the handler.
    notificationService.markDone.calls.reset();
    btn!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(notificationService.markDone).not.toHaveBeenCalled();
    btn!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
    expect(notificationService.markDone).toHaveBeenCalledTimes(1);
  });

  it('markDone calls preventDefault + stopPropagation to avoid re-dispatch to the <li>', async () => {
    await buildModule();
    const event = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
    const preventDefaultSpy = spyOn(event, 'preventDefault').and.callThrough();
    const stopPropagationSpy = spyOn(event, 'stopPropagation').and.callThrough();
    component.markDone(buildNotification(), event);
    expect(preventDefaultSpy).toHaveBeenCalled();
    expect(stopPropagationSpy).toHaveBeenCalled();
  });

  it('markDone routes through notificationService.markDone with the same notification', async () => {
    await buildModule();
    const n = buildNotification({ reminderId: 42, emailId: 99 });
    component.markDone(
      n,
      new MouseEvent('mousedown', { bubbles: true, cancelable: true })
    );
    expect(notificationService.markDone).toHaveBeenCalledWith(n);
  });

  it('markDone swallows notificationService errors so the page never hangs', async () => {
    await buildModule();
    notificationService.markDone.and.returnValue(throwError(() => new Error('boom')));
    expect(() =>
      component.markDone(
        buildNotification(),
        new MouseEvent('mousedown', { bubbles: true, cancelable: true })
      )
    ).not.toThrow();
  });

  it('markDone runs synchronously — no setTimeout deferral (regression for v3 freeze)', async () => {
    await buildModule();
    const start = performance.now();
    component.markDone(
      buildNotification(),
      new MouseEvent('mousedown', { bubbles: true, cancelable: true })
    );
    const elapsed = performance.now() - start;
    // The handler must complete inside the same microtask. If the
    // PATCH were deferred (setTimeout(0)), the call would return
    // in <1ms but the markDone spy would not yet have been
    // invoked — and we want to assert it WAS invoked synchronously.
    expect(notificationService.markDone).toHaveBeenCalledTimes(1);
    // Generous bound: 50ms covers test-suite jitter on slow CI.
    expect(elapsed).toBeLessThan(50);
  });
});
