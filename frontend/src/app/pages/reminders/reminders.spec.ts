import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { RemindersComponent } from './reminders';
import { ReminderService } from '../../services/reminder.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { UserInfo } from '../../models/user-info.model';
import { Reminder } from '../../models/reminder.model';
import { signal } from '@angular/core';

const buildReminder = (overrides: Partial<Reminder> = {}): Reminder => ({
  id: 1,
  emailId: 10,
  reminderDate: '2026-06-24T10:00:00',
  message: 'Llamar al banco',
  done: false,
  createdAt: '2026-06-22T09:00:00Z',
  updatedAt: '2026-06-22T09:00:00Z',
  ...overrides,
});

interface StubRefs {
  reminderService: {
    list: jasmine.Spy;
    update: jasmine.Spy;
    delete: jasmine.Spy;
    getByEmail: jasmine.Spy;
    create: jasmine.Spy;
  };
  toast: {
    success: jasmine.Spy;
    error: jasmine.Spy;
    info: jasmine.Spy;
    warning: jasmine.Spy;
  };
}

class AuthServiceStub {
  user = signal<UserInfo | null>({
    id: 1,
    name: 'U',
    email: 'u@example.com',
    pictureUrl: '',
    role: 'PREMIUM',
    trialEndDate: null,
    trialExpired: false,
    accessibilityMode: true,
  });
}

describe('RemindersComponent', () => {
  let fixture: ComponentFixture<RemindersComponent>;
  let component: RemindersComponent;
  let httpTesting: HttpTestingController;
  let stubs: StubRefs;

  const buildModule = async (role: UserInfo['role'] = 'PREMIUM'): Promise<void> => {
    const authStub = new AuthServiceStub();
    authStub.user = signal<UserInfo | null>({
      id: 1, name: 'U', email: 'u@example.com', pictureUrl: '',
      role, trialEndDate: null, trialExpired: false, accessibilityMode: true,
    });

    stubs = {
      reminderService: {
        list: jasmine.createSpy('list').and.returnValue(of([])),
        update: jasmine.createSpy('update').and.returnValue(of()),
        delete: jasmine.createSpy('delete').and.returnValue(of(undefined)),
        getByEmail: jasmine.createSpy('getByEmail').and.returnValue(of(null)),
        create: jasmine.createSpy('create').and.returnValue(of()),
      },
      toast: {
        success: jasmine.createSpy('success'),
        error: jasmine.createSpy('error'),
        info: jasmine.createSpy('info'),
        warning: jasmine.createSpy('warning'),
      },
    };

    await TestBed.configureTestingModule({
      imports: [RemindersComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
        { provide: ReminderService, useValue: stubs.reminderService },
        { provide: ToastService, useValue: stubs.toast },
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RemindersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  afterEach(() => {
    httpTesting.verify();
  });

  it('shows the empty state when the user is on TRIAL', async () => {
    await buildModule('TRIAL');
    expect(fixture.nativeElement.querySelector('.reminders-trial')).toBeTruthy();
    expect(stubs.reminderService.list).not.toHaveBeenCalled();
  });

  it('fetches and renders the reminder list on init for PREMIUM', async () => {
    await buildModule('PREMIUM');
    const items = [buildReminder({ id: 1 }), buildReminder({ id: 2, done: true })];
    stubs.reminderService.list.and.returnValue(of(items));
    component.loadListPublic();
    fixture.detectChanges();
    expect(stubs.reminderService.list).toHaveBeenCalledWith('all');
    const cards = fixture.nativeElement.querySelectorAll('.reminder-item');
    expect(cards.length).toBe(2);
  });

  it('shows the empty state when the list is empty', async () => {
    await buildModule('PREMIUM');
    stubs.reminderService.list.and.returnValue(of([]));
    component.loadListPublic();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.reminders-empty')).toBeTruthy();
  });

  it('filters by pending when the user clicks "Pendientes"', async () => {
    await buildModule('PREMIUM');
    const items = [
      buildReminder({ id: 1, done: false }),
      buildReminder({ id: 2, done: true }),
    ];
    stubs.reminderService.list.and.returnValue(of(items));
    component.loadListPublic();
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.reminders-filters button') as unknown as Element[]);
    const pendingBtn = buttons.find((b: Element) => b.textContent?.includes('Pendientes')) as HTMLButtonElement;
    pendingBtn.click();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.reminder-item');
    expect(cards.length).toBe(1);
    expect(cards[0].classList.contains('done')).toBeFalse();
  });

  it('filters by done when the user clicks "Completados"', async () => {
    await buildModule('PREMIUM');
    const items = [
      buildReminder({ id: 1, done: false }),
      buildReminder({ id: 2, done: true }),
    ];
    stubs.reminderService.list.and.returnValue(of(items));
    component.loadListPublic();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.reminders-filters button') as unknown as Element[]);
    const doneBtn = buttons.find((b: Element) => b.textContent?.includes('Completados')) as HTMLButtonElement;
    doneBtn.click();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.reminder-item');
    expect(cards.length).toBe(1);
    expect(cards[0].classList.contains('done')).toBeTrue();
  });

  it('toggles done when the checkbox is clicked', async () => {
    await buildModule('PREMIUM');
    const original = buildReminder({ id: 5, done: false });
    const updated = buildReminder({ id: 5, done: true });
    stubs.reminderService.list.and.returnValue(of([original]));
    stubs.reminderService.update.and.returnValue(of(updated));
    component.loadListPublic();
    fixture.detectChanges();

    const check = fixture.nativeElement.querySelector('.reminder-check') as HTMLButtonElement;
    check.click();

    expect(stubs.reminderService.update).toHaveBeenCalledWith(5, { done: true });
    expect(stubs.toast.success).toHaveBeenCalled();
    expect(component.items()[0].done).toBeTrue();
  });

  it('shows an error toast when the load fails', async () => {
    await buildModule('PREMIUM');
    stubs.reminderService.list.and.returnValue(throwError(() => new Error('boom')));
    component.loadListPublic();
    fixture.detectChanges();
    expect(stubs.toast.error).toHaveBeenCalled();
    expect(component.error()).toBeTrue();
  });

  it('opens the confirm dialog and deletes the reminder on confirm', async () => {
    await buildModule('PREMIUM');
    const reminder = buildReminder({ id: 7 });
    stubs.reminderService.list.and.returnValue(of([reminder]));
    stubs.reminderService.delete.and.returnValue(of(undefined));
    component.loadListPublic();
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.reminder-actions button') as unknown as Element[]);
    const deleteBtn = buttons.find((b: Element) => b.getAttribute('title') === 'Eliminar') as HTMLButtonElement;
    deleteBtn.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-confirm-dialog')).toBeTruthy();

    const confirmBtn = fixture.nativeElement.querySelector('app-confirm-dialog .btn-danger') as HTMLButtonElement;
    confirmBtn.click();

    expect(stubs.reminderService.delete).toHaveBeenCalledWith(7);
    expect(component.items().length).toBe(0);
  });
});
