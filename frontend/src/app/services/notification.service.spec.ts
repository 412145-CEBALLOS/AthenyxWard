import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { NotificationService } from './notification.service';
import { ReminderService } from './reminder.service';
import { ToastService } from './toast.service';
import { AppConfigInitializerService } from './app-config-initializer.service';
import { AuthService } from './auth.service';
import { UpcomingNotification } from '../models/notification.model';
import { of, throwError, Subject } from 'rxjs';

const premiumUser = { id: 1, email: 'p@p.com', name: 'P', role: 'PREMIUM', pictureUrl: '', trialEndDate: null, trialExpired: false, accessibilityMode: false, termsAcceptedAt: null, termsVersion: null };

function makeMockAppConfig() {
  return {
    pollIntervalSeconds: signal(120),
    supportEmail: signal('s@s.com'),
    loading: signal(false),
    riskThresholds: signal({ low: 40, medium: 70 }),
    aiEnabled: signal(true),
    load: jasmine.createSpy('load'),
  };
}

function makeMockAuth() {
  return {
    user: signal(null).asReadonly(),
    currentUser: signal(null),
    isLoggedIn: signal(false),
    refreshFailed: signal(false),
  };
}

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;
  let reminderService: { update: jasmine.Spy };
  let toast: {
    warning: jasmine.Spy;
    error: jasmine.Spy;
    success: jasmine.Spy;
    info: jasmine.Spy;
  };
  let mockAppConfig: ReturnType<typeof makeMockAppConfig>;
  let mockAuth: ReturnType<typeof makeMockAuth>;

  const inOneHour: UpcomingNotification = {
    reminderId: 1, emailId: 10, emailSubject: 'Soon', emailSender: 'a@b.com',
    message: 'msg', reminderDate: new Date(Date.now() + 30 * 60_000).toISOString(),
    isOverdue: false,
  };
  const overdue: UpcomingNotification = {
    reminderId: 2, emailId: 11, emailSubject: 'Due', emailSender: 'a@b.com',
    message: null, reminderDate: new Date(Date.now() - 3600_000).toISOString(),
    isOverdue: true,
  };
  const farFuture: UpcomingNotification = {
    reminderId: 3, emailId: 12, emailSubject: 'Later', emailSender: 'a@b.com',
    message: null, reminderDate: new Date(Date.now() + 25 * 3600_000).toISOString(),
    isOverdue: false,
  };

  beforeEach(() => {
    reminderService = { update: jasmine.createSpy('update') };
    toast = {
      warning: jasmine.createSpy('warning'),
      error: jasmine.createSpy('error'),
      success: jasmine.createSpy('success'),
      info: jasmine.createSpy('info'),
    };
    mockAppConfig = makeMockAppConfig();
    mockAuth = makeMockAuth();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReminderService, useValue: reminderService },
        { provide: ToastService, useValue: toast },
        { provide: AppConfigInitializerService, useValue: mockAppConfig },
        { provide: AuthService, useValue: mockAuth },
        NotificationService,
      ],
    });

    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    service.stopPollingLoop();
    if (http) http.verify();
  });

  // --- fetchOnce ---

  it('hits the upcoming endpoint with the correct URL', () => {
    service.fetchOnce().subscribe();
    const req = http.expectOne('/api/notifications/upcoming');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('returns an empty list on 403 (TRIAL user)', () => {
    let received: UpcomingNotification[] | undefined;
    service.fetchOnce().subscribe((items) => (received = items));
    http.expectOne('/api/notifications/upcoming')
      .flush('forbidden', { status: 403, statusText: 'Forbidden' });
    expect(received).toEqual([]);
  });

  // --- polling ---

  it('starts polling: first call is immediate, then once per interval', fakeAsync(() => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    tick(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    // Stop polling to prevent pending interval requests
    service.stopPollingLoop();
  }));

  it('updates the notifications signal after a successful poll', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue, inOneHour, farFuture]);
    expect(service.notifications().length).toBe(3);
    expect(service.count()).toBe(3);
    expect(service.overdueCount()).toBe(1);
  });

  it('fires a warning toast with action button for an overdue item', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);
    expect(toast.warning).toHaveBeenCalledTimes(1);
    const args = toast.warning.calls.mostRecent().args;
    expect(args[0]).toContain('Due');
    expect(args[1]?.action?.label).toBe('Marcar hecho');
  });

  it('does NOT re-fire the warning toast for the same reminder within the session', () => {
    service.markShown(overdue.reminderId);
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it('fires an info toast for items due in the next hour', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inOneHour]);
    expect(toast.info).toHaveBeenCalledTimes(1);
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it('does NOT fire any toast for items more than 24 hours away', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([farFuture]);
    expect(toast.info).not.toHaveBeenCalled();
    expect(toast.warning).not.toHaveBeenCalled();
    expect(service.notifications().length).toBe(1);
  });

  it('fires an info toast for items due in the next 12 hours with a day-formatted message', () => {
    const inTwelveHours: UpcomingNotification = {
      reminderId: 4, emailId: 13, emailSubject: 'Mañana', emailSender: 'x@y.com',
      message: null, reminderDate: new Date(Date.now() + 12 * 3600_000).toISOString(),
      isOverdue: false,
    };
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inTwelveHours]);
    expect(toast.info).toHaveBeenCalledTimes(1);
    const message = toast.info.calls.mostRecent().args[0];
    expect(message).toContain('Mañana');
    expect(message).toMatch(/en (\d+ h|\d+ d)/);
  });

  it('does NOT re-fire the info toast for the same reminder within the session', () => {
    service.markShown(inOneHour.reminderId);
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inOneHour]);
    expect(toast.info).not.toHaveBeenCalled();
  });

  // --- overdueTimers scheduling ---

  it('fires warning immediately when isOverdue=true arrives in poll', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);
    expect(toast.warning).toHaveBeenCalledTimes(1);
  });

  it('schedules setTimeout for items due within 24h', () => {
    const dueSoon: UpcomingNotification = {
      reminderId: 10, emailId: 50, emailSubject: 'Due Soon', emailSender: 'x@y.com',
      message: null, reminderDate: new Date(Date.now() + 10_000).toISOString(),
      isOverdue: false,
    };
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([dueSoon]);

    const timers = (service as any).overdueTimers as Map<number, any>;
    expect(timers.has(10)).toBeTrue();
  });

  it('does not schedule timer for already-overdue item', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);

    const timers = (service as any).overdueTimers as Map<number, any>;
    expect(timers.has(overdue.reminderId)).toBeFalse();
    expect(toast.warning).toHaveBeenCalledTimes(1);
  });

  it('does not schedule timer for items more than 24h away', () => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([farFuture]);

    const timers = (service as any).overdueTimers as Map<number, any>;
    expect(timers.has(farFuture.reminderId)).toBeFalse();
  });

  it('clears overdue timer when reminder is removed', () => {
    const dueSoon: UpcomingNotification = {
      reminderId: 10, emailId: 50, emailSubject: 'Due Soon', emailSender: 'x@y.com',
      message: null, reminderDate: new Date(Date.now() + 10_000).toISOString(),
      isOverdue: false,
    };
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([dueSoon]);

    const timers = (service as any).overdueTimers as Map<number, any>;
    expect(timers.has(10)).toBeTrue();

    service.removeLocally(10);
    expect(timers.has(10)).toBeFalse();
  });

  it('clears overdue timer when reminder is marked done', () => {
    reminderService.update.and.returnValue(of({
      id: 10, emailId: 50, reminderDate: new Date(Date.now() + 10_000).toISOString(),
      message: null, done: true, createdAt: '', updatedAt: '',
    }));
    const dueSoon: UpcomingNotification = {
      reminderId: 10, emailId: 50, emailSubject: 'Due Soon', emailSender: 'x@y.com',
      message: null, reminderDate: new Date(Date.now() + 10_000).toISOString(),
      isOverdue: false,
    };
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([dueSoon]);

    const timers = (service as any).overdueTimers as Map<number, any>;
    expect(timers.has(10)).toBeTrue();

    service.markDoneLocally(10);
    expect(timers.has(10)).toBeFalse();
  });

  // --- markDone / markDoneById ---

  it('markDone updates the backend, removes the entry locally, and fires done$', () => {
    reminderService.update.and.returnValue(of({
      id: 2, emailId: 11, reminderDate: overdue.reminderDate,
      message: null, done: true, createdAt: '', updatedAt: '',
    }));
    service.notifications.set([overdue, inOneHour]);

    let completed = false;
    let emittedId: number | undefined;
    const sub = service.done$.subscribe((id) => (emittedId = id));
    service.markDone(overdue).subscribe(() => (completed = true));
    sub.unsubscribe();

    expect(reminderService.update).toHaveBeenCalledWith(2, { done: true });
    expect(completed).toBeTrue();
    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].reminderId).toBe(1);
    expect(emittedId).toBe(2);
  });

  it('markDone shows an error toast when the backend fails', () => {
    reminderService.update.and.returnValue(throwError(() => new Error('boom')));
    let errored = false;
    service.markDone(overdue).subscribe({
      error: () => (errored = true),
    });
    expect(errored).toBeTrue();
    expect(toast.error).toHaveBeenCalled();
    expect((service as any).inflightMarkDone.has(overdue.reminderId)).toBeFalse();
  });

  it('markDoneById with 404 removes notification locally and does NOT show error toast', () => {
    service.notifications.set([overdue, inOneHour]);
    const notFoundError = { status: 404 } as HttpErrorResponse;
    reminderService.update.and.returnValue(throwError(() => notFoundError));
    let errored = false;
    service.markDoneById(overdue.reminderId).subscribe({
      error: () => (errored = true),
    });
    expect(errored).toBeFalse();
    expect(toast.error).not.toHaveBeenCalled();
    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].reminderId).toBe(1);
    expect((service as any).inflightMarkDone.has(overdue.reminderId)).toBeFalse();
  });

  it('markDoneById with 404 fires done$ so subscribers stay in sync', () => {
    service.notifications.set([overdue, inOneHour]);
    const notFoundError = { status: 404 } as HttpErrorResponse;
    reminderService.update.and.returnValue(throwError(() => notFoundError));
    let emittedId: number | undefined;
    const sub = service.done$.subscribe((id) => (emittedId = id));
    service.markDoneById(overdue.reminderId).subscribe();
    sub.unsubscribe();
    expect(emittedId).toBe(overdue.reminderId);
  });

  it('markDone is idempotent while a request is in flight', () => {
    const pending = new Subject<void>();
    reminderService.update.and.returnValue(pending);
    service.markDone(overdue).subscribe();
    service.markDone(overdue).subscribe();
    expect(reminderService.update).toHaveBeenCalledTimes(1);
    pending.complete();
  });

  it('markDoneById updates the backend, removes the entry locally, and fires done$', () => {
    reminderService.update.and.returnValue(of({
      id: 2, emailId: 11, reminderDate: overdue.reminderDate,
      message: null, done: true, createdAt: '', updatedAt: '',
    }));
    service.notifications.set([overdue, inOneHour]);

    let completed = false;
    let emittedId: number | undefined;
    const sub = service.done$.subscribe((id) => (emittedId = id));
    service.markDoneById(overdue.reminderId).subscribe(() => (completed = true));
    sub.unsubscribe();

    expect(reminderService.update).toHaveBeenCalledWith(2, { done: true });
    expect(completed).toBeTrue();
    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].reminderId).toBe(1);
    expect(emittedId).toBe(2);
  });

  it('markDoneById is idempotent while a request is in flight', () => {
    const pending = new Subject<void>();
    reminderService.update.and.returnValue(pending);
    service.markDoneById(overdue.reminderId).subscribe();
    service.markDoneById(overdue.reminderId).subscribe();
    expect(reminderService.update).toHaveBeenCalledTimes(1);
    pending.complete();
  });

  // --- markDoneLocally ---

  it('markDoneLocally removes the item and fires done$', () => {
    service.notifications.set([overdue, inOneHour]);
    let emittedId: number | undefined;
    const sub = service.done$.subscribe((id) => (emittedId = id));
    service.markDoneLocally(overdue.reminderId);
    sub.unsubscribe();
    expect(service.notifications().length).toBe(1);
    expect(emittedId).toBe(overdue.reminderId);
  });

  // --- stopPolling ---

  it('stopPolling clears the interval', fakeAsync(() => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    service.stopPollingLoop();
    tick(120_000);
    http.expectNone('/api/notifications/upcoming');
  }));

  // --- US 2.7 hardening: poll/markDone race ---

  it('poll skips the network round-trip while a markDone is in flight', fakeAsync(() => {
    service.startPollingLoop(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    tick(10);
    const inFlight = (service as any).inflightMarkDone as Set<number>;
    expect(inFlight.size).toBe(0);

    reminderService.update.and.returnValue(of({
      id: 2, emailId: 11, reminderDate: overdue.reminderDate,
      message: null, done: true, createdAt: '', updatedAt: '',
    }));
    service.markDoneById(overdue.reminderId).subscribe();
    expect(inFlight.has(overdue.reminderId)).toBeFalse();
    // Stop polling before test ends to prevent pending interval request
    service.stopPollingLoop();
  }));

  // --- legacy startPolling / stopPolling (now no-ops) ---

  it('startPolling is now a no-op (self-reactive via effect)', () => {
    (service as any).startPolling(60_000);
    http.expectNone('/api/notifications/upcoming');
  });

  it('stopPolling is now a no-op (self-reactive via effect)', () => {
    (service as any).stopPolling();
  });
});
