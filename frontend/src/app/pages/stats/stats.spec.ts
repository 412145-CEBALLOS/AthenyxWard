import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { StatsComponent } from './stats';
import { AuthService } from '../../services/auth.service';
import { UserInfo } from '../../models/user-info.model';

function makeUser(role: 'ADMIN' | 'PREMIUM' | 'TRIAL'): UserInfo {
  return {
    id: 1,
    name: 'Test User',
    email: 'test@example.com',
    pictureUrl: '',
    role,
    trialEndDate: null,
    trialExpired: false,
    accessibilityMode: true,
    termsAcceptedAt: null,
    termsVersion: null,
    lastLoginAt: null,
    emailVerified: true,
  };
}

describe('Stats', () => {
  let mockAuth: { user: ReturnType<typeof signal<UserInfo | null>> };
  let component: StatsComponent;
  let fixture: ComponentFixture<StatsComponent>;

  beforeEach(async () => {
    mockAuth = { user: signal<UserInfo | null>(null) };

    await TestBed.configureTestingModule({
      imports: [StatsComponent],
      providers: [
        { provide: AuthService, useValue: mockAuth },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(StatsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    mockAuth.user.set(makeUser('TRIAL'));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show user dashboard when role is TRIAL', () => {
    mockAuth.user.set(makeUser('TRIAL'));
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
    expect(component.title()).toBe('Mis estadÃ­sticas');
    expect(component.subtitle()).toContain('tu actividad');
  });

  it('should show user dashboard when role is PREMIUM', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
    expect(component.title()).toBe('Mis estadÃ­sticas');
  });

  it('should show admin dashboard when role is ADMIN', () => {
    mockAuth.user.set(makeUser('ADMIN'));
    fixture.detectChanges();
    expect(component.isAdmin()).toBeTrue();
    expect(component.title()).toBe('EstadÃ­sticas (panel global)');
    expect(component.subtitle()).toContain('plataforma');
  });

  it('should expose trial usage only for TRIAL role', () => {
    mockAuth.user.set(makeUser('TRIAL'));
    fixture.detectChanges();
    expect(component.userTrialUsage()).toEqual({ used: 8, total: 20 });
  });

  it('should hide trial usage for PREMIUM users', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.userTrialUsage()).toBeNull();
  });

  it('should hide trial usage for ADMIN users', () => {
    mockAuth.user.set(makeUser('ADMIN'));
    fixture.detectChanges();
    expect(component.userTrialUsage()).toBeNull();
  });

  it('should update period via setPeriod', () => {
    expect(component.period()).toBe('week');
    component.setPeriod('month');
    expect(component.period()).toBe('month');
    expect(component.periodLabel()).toBe('Ãšltimo mes');
    component.setPeriod('year');
    expect(component.periodLabel()).toBe('Ãšltimo aÃ±o');
  });

  it('should compute time since last threat', () => {
    expect(component.timeSinceLastThreat()).toMatch(/hace/);
  });
});
