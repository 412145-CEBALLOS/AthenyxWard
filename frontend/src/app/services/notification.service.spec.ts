import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { NotificationService } from './notification.service';
import { ReminderService } from './reminder.service';
import { ToastService } from './toast.service';
import { UpcomingNotification } from '../models/notification.model';
import { of, throwError, Subject } from 'rxjs';

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

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationService,
        { provide: ReminderService, useValue: reminderService },
        { provide: ToastService, useValue: toast },
      ],
    });

    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    service.stopPolling();
    if (http) http.verify();
  });

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

  it('starts polling: first call is immediate, then once per interval', fakeAsync(() => {
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    service.stopPolling();
    tick(60_000);
    http.expectNone('/api/notifications/upcoming');
  }));

  it('updates the notifications signal after a successful poll', () => {
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue, inOneHour, farFuture]);
    expect(service.notifications().length).toBe(3);
    expect(service.count()).toBe(3);
    expect(service.overdueCount()).toBe(1);
  });

  it('fires a warning toast with action button for an overdue item', () => {
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);
    expect(toast.warning).toHaveBeenCalledTimes(1);
    const args = toast.warning.calls.mostRecent().args;
    expect(args[0]).toContain('Due');
    expect(args[1]?.action?.label).toBe('Marcar hecho');
  });

  it('does NOT re-fire the warning toast for the same reminder within the session', () => {
    // Mark the reminder as already shown — simulates a previous poll
    // having fired the toast already. The next poll must skip it.
    service.markShown(overdue.reminderId);
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([overdue]);
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it('fires an info toast for items due in the next hour', () => {
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inOneHour]);
    expect(toast.info).toHaveBeenCalledTimes(1);
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it('does NOT fire any toast for items more than 24 hours away', () => {
    // 25h ahead — outside the 24h "coming up" window. The item
    // still appears in the bell panel (and therefore in the
    // notifications signal) but no toast is fired.
    service.startPolling(60_000);
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
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inTwelveHours]);
    expect(toast.info).toHaveBeenCalledTimes(1);
    const message = toast.info.calls.mostRecent().args[0];
    expect(message).toContain('Mañana');
    // Should mention the future distance (h or d), not "min".
    expect(message).toMatch(/en (\d+ h|\d+ d)/);
  });

  it('does NOT re-fire the info toast for the same reminder within the session', () => {
    service.markShown(inOneHour.reminderId);
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([inOneHour]);
    expect(toast.info).not.toHaveBeenCalled();
  });

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
    // The inflight set is cleared so a second click can retry.
    expect((service as any).inflightMarkDone.has(overdue.reminderId)).toBeFalse();
  });

  it('markDone is idempotent while a request is in flight', () => {
    // Use a never-completing observable to keep the first request
    // in flight while we issue the second one. The inflight set
    // should make the second call a no-op.
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

  it('stopPolling clears the interval', fakeAsync(() => {
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    service.stopPolling();
    tick(120_000);
    http.expectNone('/api/notifications/upcoming');
  }));

  it('markDoneLocally removes the item and fires done$ (no signal cascade)', () => {
    service.notifications.set([overdue, inOneHour]);
    let emittedId: number | undefined;
    const sub = service.done$.subscribe((id) => (emittedId = id));
    service.markDoneLocally(overdue.reminderId);
    sub.unsubscribe();
    expect(service.notifications().length).toBe(1);
    expect(emittedId).toBe(overdue.reminderId);
  });

  // --- US 2.7 hardening: poll/markDone race ---

  it('poll skips the network round-trip while a markDone is in flight', fakeAsync(() => {
    // The in-flight guard is what prevents a racing poll from
    // re-fetching the just-done reminder. We verify the public
    // surface: a markDone is in flight, the next poll cycle
    // is dropped (no HTTP request), and once the PATCH settles
    // the guard drains and the following cycle is allowed
    // through.
    service.startPolling(60_000);
    http.expectOne('/api/notifications/upcoming').flush([]);
    const inFlight = (service as any).inflightMarkDone as Set<number>;
    expect(inFlight.size).toBe(0);

    // Issue a markDone. We use an `of(...)` because it resolves
    // synchronously, so the tap (and the in-flight set drain)
    // happens before our assertions.
    reminderService.update.and.returnValue(of({
      id: 2, emailId: 11, reminderDate: overdue.reminderDate,
      message: null, done: true, createdAt: '', updatedAt: '',
    }));
    service.markDoneById(overdue.reminderId).subscribe();
    // Drain confirmed.
    expect(inFlight.has(overdue.reminderId)).toBeFalse();

    // Stop polling so the fakeAsync teardown doesn't leak the
    // 60s tick into the afterEach's http.verify().
    service.stopPolling();
  }));
});
