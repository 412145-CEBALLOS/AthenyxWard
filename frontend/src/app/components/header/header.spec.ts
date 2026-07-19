import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { Event as RouterEvent, NavigationEnd, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { HeaderComponent } from './header';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { EmailSearchService } from '../../services/email-search.service';
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
  user = (): UserInfo => ({
    id: 1, name: 'U', email: 'u@example.com', pictureUrl: '',
    role: 'PREMIUM', trialEndDate: null, trialExpired: false, accessibilityMode: true, termsAcceptedAt: null, termsVersion: null,
  });
}

class RouterStub {
  url = '/home';
  events: Subject<RouterEvent> = new Subject<RouterEvent>();
  navigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
}

describe('HeaderComponent â€” bell markDone', () => {
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
        { provide: Router, useValue: new RouterStub() },
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
    component.notificationsOpen.set(true);
    fixture.detectChanges();
    const btn: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '.notifications-action'
    );
    expect(btn).toBeTruthy();
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

  it('markDone runs synchronously â€” no setTimeout deferral (regression for v3 freeze)', async () => {
    await buildModule();
    const start = performance.now();
    component.markDone(
      buildNotification(),
      new MouseEvent('mousedown', { bubbles: true, cancelable: true })
    );
    const elapsed = performance.now() - start;
    expect(notificationService.markDone).toHaveBeenCalledTimes(1);
    expect(elapsed).toBeLessThan(50);
  });
});

describe('HeaderComponent â€” US 3.7 search bar', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let component: HeaderComponent;
  let emailSearch: EmailSearchService;

  const buildModule = async (): Promise<void> => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: AuthService, useValue: new AuthServiceStub() },
        { provide: NotificationService, useValue: {
            markDone: () => of(undefined),
            notifications: () => [],
            startPolling: () => {},
            stopPolling: () => {},
          } },
        { provide: Router, useValue: new RouterStub() },
      ],
    }).compileComponents();

    emailSearch = TestBed.inject(EmailSearchService);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('isHomeRoute is true when the active URL is /home', async () => {
    await buildModule();
    expect(component.isHomeRoute()).toBeTrue();
  });

  it('isHomeRoute is false when the active URL is not /home', async () => {
    await buildModule();
    const router = TestBed.inject(Router) as unknown as RouterStub;
    router.url = '/plan';
    (router.events as Subject<RouterEvent>).next(new NavigationEnd(1, '/plan', '/plan'));
    expect(component.isHomeRoute()).toBeFalse();
  });

  it('onSearchFocus opens the search dropdown', async () => {
    await buildModule();
    component.onSearchFocus();
    expect(emailSearch.isOpen()).toBeTrue();
  });

  it('onSearchBlur closes the search dropdown after a short delay', fakeAsync(async () => {
    await buildModule();
    component.onSearchFocus();
    expect(emailSearch.isOpen()).toBeTrue();
    component.onSearchBlur();
    tick(149);
    expect(emailSearch.isOpen()).toBeTrue();
    tick(2);
    expect(emailSearch.isOpen()).toBeFalse();
  }));

  it('onSearchEscape closes the search dropdown immediately and blurs the input', async () => {
    await buildModule();
    component.onSearchFocus();
    const input = document.createElement('input');
    const blurSpy = spyOn(input, 'blur');
    const event = { preventDefault: jasmine.createSpy('preventDefault'), target: input } as unknown as globalThis.Event;
    component.onSearchEscape(event);
    expect(emailSearch.isOpen()).toBeFalse();
    expect(event.preventDefault).toHaveBeenCalled();
    expect(blurSpy).toHaveBeenCalled();
  });

  it('onSearchEnter applies the current term to the inbox and closes the dropdown', async () => {
    await buildModule();
    emailSearch.set('paypal');
    component.onSearchFocus();
    const seen: string[] = [];
    const sub = emailSearch.inboxApply$.subscribe((v) => seen.push(v));
    const input = document.createElement('input');
    const blurSpy = spyOn(input, 'blur');
    const event = { preventDefault: jasmine.createSpy('preventDefault'), target: input } as unknown as globalThis.Event;
    component.onSearchEnter(event);
    expect(seen).toEqual(['paypal']);
    expect(emailSearch.isOpen()).toBeFalse();
    expect(event.preventDefault).toHaveBeenCalled();
    expect(blurSpy).toHaveBeenCalled();
    sub.unsubscribe();
  });

  it('onSearchEnter with an empty term does NOT emit inboxApply$', async () => {
    await buildModule();
    const seen: string[] = [];
    const sub = emailSearch.inboxApply$.subscribe((v) => seen.push(v));
    const input = document.createElement('input');
    const event = { preventDefault: jasmine.createSpy('preventDefault'), target: input } as unknown as globalThis.Event;
    component.onSearchEnter(event);
    expect(seen).toEqual([]);
    sub.unsubscribe();
  });

  it('onClearSearch clears the term through the service', fakeAsync(async () => {
    await buildModule();
    emailSearch.set('paypal');
    component.onClearSearch();
    tick(300);
    expect(emailSearch.term()).toBe('');
  }));
});
