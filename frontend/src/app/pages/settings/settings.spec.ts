import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { SettingsComponent } from './settings';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { ThemeService } from '../../services/theme.service';
import { environment } from '../../../environments/environment';

const mockUser = {
  id: 1,
  name: 'Test User',
  email: 'test@example.com',
  pictureUrl: '',
  role: 'TRIAL' as const,
  trialEndDate: null,
  trialExpired: false,
  accessibilityMode: true,
  termsAcceptedAt: null,
  termsVersion: null,
  lastLoginAt: null,
  emailVerified: true,
};

const mockUsage = {
  user: mockUser,
  analysis: { used: 5, limit: 20, trialEndDate: null, expired: false },
  reminders: { active: 2, done: 3 },
  emails: { total: 10, important: 1, hidden: 0, deleted: 0 },
  sessions: { active: 1 },
  dataInventory: {
    emails: 10,
    analyses: 5,
    aiExplanations: 2,
    reminders: 5,
    auditEvents: 20,
    oldestRecordAt: '2025-03-01T10:00:00',
  },
};

const mockSessions = [
  {
    id: 1,
    familyId: 'family-1',
    userAgent: 'Chrome on Windows',
    ip: '192.168.1.1',
    issuedAt: '2025-01-01T10:00:00',
    lastUsedAt: '2025-06-01T10:00:00',
    current: true,
  },
];

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let httpMock: HttpTestingController;
  let mockAuth: jasmine.SpyObj<AuthService>;
  let mockUserSvc: jasmine.SpyObj<UserService>;
  let mockToast: jasmine.SpyObj<ToastService>;
  let mockAppConfig: { supportEmail: ReturnType<typeof signal<string>> };
  let mockTheme: jasmine.SpyObj<ThemeService>;

  beforeEach(async () => {
    mockAuth = jasmine.createSpyObj('AuthService', [
      'updateAccessibilityMode', 'logoutAll', 'currentUser',
    ]);
    (mockAuth as any).user = signal(mockUser);
    (mockAuth as any).currentUser = signal(mockUser);
    mockAuth.updateAccessibilityMode.and.returnValue(of(mockUser));
    mockAuth.logoutAll.and.returnValue(of({ message: 'ok', revoked: 1 }));

    mockUserSvc = jasmine.createSpyObj('UserService', ['getUsage', 'getSessions', 'revokeSession']);
    mockUserSvc.getUsage.and.returnValue(of(mockUsage));
    mockUserSvc.getSessions.and.returnValue(of(mockSessions));
    mockUserSvc.revokeSession.and.returnValue(of(undefined));

    mockToast = jasmine.createSpyObj('ToastService', ['error']);

    mockAppConfig = {
      supportEmail: signal('soporte@athenyx.com'),
    };

    mockTheme = jasmine.createSpyObj('ThemeService', ['setTheme']);
    (mockTheme as any).theme = signal<'auto' | 'light' | 'dark'>('auto');

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuth },
        { provide: UserService, useValue: mockUserSvc },
        { provide: ToastService, useValue: mockToast },
        { provide: AppConfigInitializerService, useValue: mockAppConfig },
        { provide: ThemeService, useValue: mockTheme },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads usage on init', () => {
    expect(mockUserSvc.getUsage).toHaveBeenCalled();
  });

  it('loads sessions on init', () => {
    expect(mockUserSvc.getSessions).toHaveBeenCalled();
  });

  it('renders profile section with user data', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.profile-name')?.textContent).toContain('Test User');
  });

  it('renders usage data', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.usage-count')?.textContent).toContain('5');
  });

  it('renders inventory card with counts', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.inventory-count')?.textContent).toContain('10');
  });

  it('calls updateAccessibilityMode on accessibility toggle', () => {
    component.onAccessibilityToggle(false);
    expect(mockAuth.updateAccessibilityMode).toHaveBeenCalledWith(false);
  });

  it('shows toast on accessibility toggle failure', () => {
    mockAuth.updateAccessibilityMode.and.returnValue(throwError(() => new Error('boom')));
    component.onAccessibilityToggle(false);
    expect(mockToast.error).toHaveBeenCalledWith('No se pudo guardar el modo accesibilidad');
  });

  it('opens confirm dialog on revoke request', () => {
    component.onRevokeRequest(mockSessions[0]);
    expect(component.confirmRevoke()).toEqual(mockSessions[0]);
  });

  it('closes confirm dialog on cancel', () => {
    component.onRevokeRequest(mockSessions[0]);
    component.onRevokeCancel();
    expect(component.confirmRevoke()).toBeNull();
  });

  it('revokes session and refreshes list on confirm', () => {
    component.onRevokeRequest(mockSessions[0]);
    component.onRevokeConfirm();
    expect(mockUserSvc.revokeSession).toHaveBeenCalledWith(1);
  });

  it('formats date correctly', () => {
    expect(component.formatDate('2025-06-01T10:00:00')).not.toBe('—');
    expect(component.formatDate(null)).toBe('—');
  });

  it('formats old date correctly', () => {
    const result = component.formatOldestDate('2024-01-15T10:00:00');
    expect(result).toContain('2024');
  });

  it('roleChip returns correct info', () => {
    expect(component.roleChip('ADMIN')).toEqual({ label: 'Administrador', cssClass: 'role-admin' });
    expect(component.roleChip('PREMIUM')).toEqual({ label: 'Premium', cssClass: 'role-premium' });
    expect(component.roleChip('TRIAL')).toEqual({ label: 'Prueba', cssClass: 'role-trial' });
  });

  it('trialProgressPercent calculates correctly', () => {
    expect(component.trialProgressPercent(10, 20)).toBe(50);
    expect(component.trialProgressPercent(25, 20)).toBe(100);
  });

  it('highlights the active theme based on themeService.theme()', () => {
    (mockTheme as any).theme.set('dark');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = compiled.querySelectorAll('.segmented button');
    expect(buttons[2].classList).toContain('active');
  });

  it('calls themeService.setTheme when a segmented button is clicked', () => {
    (mockTheme as any).theme.set('auto');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = compiled.querySelectorAll('.segmented button');
    (buttons[1] as HTMLButtonElement).click();
    expect(mockTheme.setTheme).toHaveBeenCalledWith('light');
  });
});
