import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StatsService } from './stats.service';
import { AdminStatsResponse, UserStatsResponse } from '../models/stats.model';
import { environment } from '../../environments/environment';

describe('StatsService', () => {
  let service: StatsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        StatsService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(StatsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch user stats with default period', () => {
    const mockResponse: UserStatsResponse = {
      period: 'week',
      kpis: [],
      dailyThreats: [],
      riskDistribution: [],
      topCategories: [],
      recentActivity: [],
      lastThreatAt: null,
      trialUsage: null,
    };

    service.getUserStats().subscribe((response) => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/stats/user?period=week`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should fetch user stats with custom period', () => {
    service.getUserStats('month').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/stats/user?period=month`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should fetch admin stats', () => {
    const mockResponse: AdminStatsResponse = {
      period: 'year',
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
    };

    service.getAdminStats('year').subscribe((response) => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/stats/admin?period=year`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
