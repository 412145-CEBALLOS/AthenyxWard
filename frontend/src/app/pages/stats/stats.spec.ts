import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { StatsComponent } from './stats';
import { AuthService } from '../../services/auth.service';
import { StatsService } from '../../services/stats.service';
import { UserInfo } from '../../models/user-info.model';
import { UserStatsResponse } from '../../models/stats.model';

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

function makeUserStats(overrides: Partial<UserStatsResponse> = {}): UserStatsResponse {
  return {
    period: 'week',
    kpis: [
      { label: 'Correos analizados', value: 10, previousValue: 8, trendPercent: 25, trendUp: true },
      { label: 'Amenazas bloqueadas', value: 1, previousValue: 2, trendPercent: -50, trendUp: false },
      { label: 'Tasa de phishing', value: 10, previousValue: 20, trendPercent: -50, trendUp: false },
      { label: 'Riesgo medio', value: 35, previousValue: 40, trendPercent: -12.5, trendUp: false },
    ],
    dailyThreats: [
      { label: 'Lun', count: 1 },
      { label: 'Mar', count: 0 },
      { label: 'Mié', count: 0 },
      { label: 'Jue', count: 0 },
      { label: 'Vie', count: 0 },
      { label: 'Sáb', count: 0 },
      { label: 'Dom', count: 0 },
    ],
    riskDistribution: [
      { level: 'GREEN', count: 9 },
      { level: 'YELLOW', count: 0 },
      { level: 'RED', count: 1 },
    ],
    topCategories: [{ category: 'URL maliciosa', count: 1 }],
    recentActivity: [],
    lastThreatAt: null,
    trialUsage: null,
    ...overrides,
  };
}

describe('Stats', () => {
  let mockAuth: { user: ReturnType<typeof signal<UserInfo | null>> };
  let mockStatsService: jasmine.SpyObj<StatsService>;
  let component: StatsComponent;
  let fixture: ComponentFixture<StatsComponent>;

  beforeEach(async () => {
    mockAuth = { user: signal<UserInfo | null>(null) };
    mockStatsService = jasmine.createSpyObj<StatsService>('StatsService', ['getUserStats', 'getAdminStats']);
    mockStatsService.getUserStats.and.returnValue(of(makeUserStats()));
    mockStatsService.getAdminStats.and.returnValue(of({
      period: 'week',
      kpis: [],
      dailyThreats: [],
      riskDistribution: [],
      userSplit: [],
      topCategories: [],
      analysisSourceSplit: [],
      engagement: { dau: 0, wau: 0, mau: 0 },
      conversionRate: { value: 0, previousValue: 0, trendPercent: 0, trendUp: true },
      signups: [],
      threatsByHour: [],
    }));

    await TestBed.configureTestingModule({
      imports: [StatsComponent],
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: StatsService, useValue: mockStatsService },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

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
    expect(component.title()).toBe('Mis estadísticas');
    expect(component.subtitle()).toContain('tu actividad');
  });

  it('should show user dashboard when role is PREMIUM', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
    expect(component.title()).toBe('Mis estadísticas');
  });

  it('should show admin dashboard when role is ADMIN', () => {
    mockAuth.user.set(makeUser('ADMIN'));
    fixture.detectChanges();
    expect(component.isAdmin()).toBeTrue();
    expect(component.title()).toBe('Estadísticas (panel global)');
    expect(component.subtitle()).toContain('plataforma');
  });

  it('should call user stats endpoint for non-admin users', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(mockStatsService.getUserStats).toHaveBeenCalledWith('week');
    expect(mockStatsService.getAdminStats).not.toHaveBeenCalled();
  });

  it('should call admin stats endpoint for admin users', () => {
    mockAuth.user.set(makeUser('ADMIN'));
    fixture.detectChanges();
    expect(mockStatsService.getAdminStats).toHaveBeenCalledWith('week');
    expect(mockStatsService.getUserStats).not.toHaveBeenCalled();
  });

  it('should expose trial usage from response', () => {
    mockStatsService.getUserStats.and.returnValue(of(makeUserStats({ trialUsage: { used: 8, total: 20 } })));
    mockAuth.user.set(makeUser('TRIAL'));
    fixture.detectChanges();
    expect(component.userTrialUsage()).toEqual({ used: 8, total: 20 });
    expect(component.userTrialPercent()).toBe(40);
  });

  it('should hide trial usage when response has none', () => {
    mockAuth.user.set(makeUser('TRIAL'));
    fixture.detectChanges();
    expect(component.userTrialUsage()).toBeNull();
  });

  it('should update period via setPeriod and reload stats', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.period()).toBe('week');
    component.setPeriod('month');
    expect(component.period()).toBe('month');
    expect(component.periodLabel()).toBe('Último mes');
    expect(mockStatsService.getUserStats).toHaveBeenCalledWith('month');
  });

  it('should format period labels correctly', () => {
    component.setPeriod('year');
    expect(component.periodLabel()).toBe('Último año');
  });

  it('should format KPI values and trends', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.formatKpiValue(component.userKpis()[0])).toBe('10');
    expect(component.formatTrend(component.userKpis()[0])).toBe('+2');
    expect(component.formatKpiValue(component.userKpis()[2])).toBe('10,0%');
    expect(component.formatTrend(component.userKpis()[2])).toBe('-10,0 pp');
  });

  it('should show loading state while fetching', fakeAsync(() => {
    mockStatsService.getUserStats.and.returnValue(of(makeUserStats()));
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should show error state when request fails', () => {
    mockStatsService.getUserStats.and.returnValue(throwError(() => new Error('fail')));
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.error()).toBe('No se pudieron cargar las estadísticas.');
    expect(component.loading()).toBeFalse();
  });

  it('should clear error and retry on retry()', () => {
    mockStatsService.getUserStats.and.returnValue(throwError(() => new Error('fail')));
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.error()).toBeTruthy();

    mockStatsService.getUserStats.and.returnValue(of(makeUserStats()));
    component.retry();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
  });

  it('should show empty state when no kpis', () => {
    mockStatsService.getUserStats.and.returnValue(of(makeUserStats({ kpis: [] })));
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.isEmpty()).toBeTrue();
  });

  it('should compute time since last threat', () => {
    mockAuth.user.set(makeUser('PREMIUM'));
    fixture.detectChanges();
    expect(component.timeSinceLastThreat()).toBe('Sin amenazas registradas');
  });
});
