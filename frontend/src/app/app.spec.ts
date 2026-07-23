import { PLATFORM_ID } from '@angular/core';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';
import { UserInfo } from './models/user-info.model';

function makeUser(accessibilityMode: boolean): UserInfo {
  return {
    id: 1,
    email: 'test@example.com',
    name: 'Test User',
    role: 'TRIAL',
    pictureUrl: '',
    trialEndDate: null,
    trialExpired: false,
    accessibilityMode,
    termsAcceptedAt: null,
    termsVersion: null,
    lastLoginAt: null,
    emailVerified: true,
  };
}

describe('App', () => {
  let authMock: { user: ReturnType<typeof signal<UserInfo | null>>; currentUser: ReturnType<typeof signal<UserInfo | null>> };
  let themeMock: jasmine.SpyObj<ThemeService>;

  beforeEach(async () => {
    authMock = {
      currentUser: signal<UserInfo | null>(null),
      user: signal<UserInfo | null>(null),
    };
    themeMock = jasmine.createSpyObj('ThemeService', ['setTheme']);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: AuthService, useValue: authMock },
        { provide: ThemeService, useValue: themeMock },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    }).compileComponents();

    document.documentElement.classList.remove('accessibility-mode');
  });

  afterEach(() => {
    document.documentElement.classList.remove('accessibility-mode');
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should have router outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should add accessibility-mode class when user has accessibility enabled', () => {
    authMock.user.set(makeUser(true));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('accessibility-mode')).toBe(true);
  });

  it('should remove accessibility-mode class when user has accessibility disabled', () => {
    authMock.user.set(makeUser(false));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('accessibility-mode')).toBe(false);
  });

  it('should react to accessibilityMode changes without reload', () => {
    authMock.user.set(makeUser(true));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('accessibility-mode')).toBe(true);

    authMock.user.set(makeUser(false));
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('accessibility-mode')).toBe(false);
  });

  it('should not force accessibility mode or theme while user is still loading', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(document.documentElement.classList.contains('accessibility-mode')).toBe(false);
    expect(themeMock.setTheme).not.toHaveBeenCalled();
  });

  it('should switch to light theme when accessibility mode is enabled', () => {
    authMock.user.set(makeUser(true));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(themeMock.setTheme).toHaveBeenCalledWith('light');
  });

  it('should not change theme when accessibility mode is disabled', () => {
    authMock.user.set(makeUser(false));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(themeMock.setTheme).not.toHaveBeenCalled();
  });

  it('should switch to light theme when accessibility mode is toggled on', () => {
    authMock.user.set(makeUser(false));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(themeMock.setTheme).not.toHaveBeenCalled();

    authMock.user.set(makeUser(true));
    fixture.detectChanges();
    expect(themeMock.setTheme).toHaveBeenCalledWith('light');
  });
});
