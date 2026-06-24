import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ReminderFormDialogComponent } from './reminder-form-dialog';
import { ReminderService } from '../../services/reminder.service';
import { ToastService } from '../../services/toast.service';
import { Reminder } from '../../models/reminder.model';

const buildReminder = (overrides: Partial<Reminder> = {}): Reminder => ({
  id: 7,
  emailId: 10,
  reminderDate: new Date(Date.now() + 86400_000).toISOString(),
  message: 'original',
  done: false,
  createdAt: '2026-06-22T09:00:00Z',
  updatedAt: '2026-06-22T09:00:00Z',
  ...overrides,
});

describe('ReminderFormDialogComponent', () => {
  let fixture: ComponentFixture<ReminderFormDialogComponent>;
  let component: ReminderFormDialogComponent;
  let update: jasmine.Spy;
  let create: jasmine.Spy;
  let getByEmail: jasmine.Spy;
  let success: jasmine.Spy;
  let error: jasmine.Spy;

  const setup = async (overrides: { reminder?: Reminder | null; emailId?: number | null } = {}): Promise<void> => {
    update = jasmine.createSpy('update').and.returnValue(of(buildReminder()));
    create = jasmine.createSpy('create').and.returnValue(of(buildReminder()));
    getByEmail = jasmine.createSpy('getByEmail').and.returnValue(of(null));
    success = jasmine.createSpy('success');
    error = jasmine.createSpy('error');

    await TestBed.configureTestingModule({
      imports: [ReminderFormDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ReminderService,
          useValue: { update, create, getByEmail, list: () => of([]), delete: () => of(undefined) },
        },
        { provide: ToastService, useValue: { success, error, info: () => {}, warning: () => {} } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReminderFormDialogComponent);
    component = fixture.componentInstance;
    if (overrides.reminder !== undefined) {
      fixture.componentRef.setInput('reminder', overrides.reminder);
    }
    if (overrides.emailId !== undefined) {
      fixture.componentRef.setInput('emailId', overrides.emailId);
    }
    fixture.detectChanges();
  };

  it('blocks submit when the user picks a past date', async () => {
    await setup({ emailId: 10 });
    const yesterday = new Date(Date.now() - 86400_000);
    component['date'].set(yesterday.toISOString().substring(0, 10));
    component['time'].set('10:00');
    fixture.detectChanges();
    expect(component.canSubmit()).toBeFalse();
    expect(component.isPast()).toBeTrue();
  });

  it('accepts a future date', async () => {
    await setup({ emailId: 10 });
    const tomorrow = new Date(Date.now() + 86400_000);
    component['date'].set(tomorrow.toISOString().substring(0, 10));
    component['time'].set('10:00');
    fixture.detectChanges();
    expect(component.isPast()).toBeFalse();
    expect(component.canSubmit()).toBeTrue();
  });

  it('forces done=false when editing (reactivates the reminder)', async () => {
    await setup({ reminder: buildReminder({ done: true, message: 'old msg' }) });
    const tomorrow = new Date(Date.now() + 86400_000);
    component['date'].set(tomorrow.toISOString().substring(0, 10));
    component['time'].set('10:00');
    component['message'].set('new msg');
    fixture.detectChanges();
    component.onSubmit();
    expect(update).toHaveBeenCalledTimes(1);
    const body = update.calls.mostRecent().args[1];
    expect(body.done).toBeFalse();
    expect(body.message).toBe('new msg');
    // The new date is sent as a UTC ISO with Z.
    expect(body.reminderDate).toMatch(/Z$/);
  });

  it('create sends a UTC ISO with Z so the backend matches the user TZ', async () => {
    await setup({ emailId: 10 });
    const tomorrow = new Date(Date.now() + 86400_000);
    component['date'].set(tomorrow.toISOString().substring(0, 10));
    component['time'].set('10:00');
    component['message'].set('msg');
    fixture.detectChanges();
    component.onSubmit();
    expect(create).toHaveBeenCalledTimes(1);
    const body = create.calls.mostRecent().args[0];
    expect(body.reminderDate).toMatch(/Z$/);
  });
});
