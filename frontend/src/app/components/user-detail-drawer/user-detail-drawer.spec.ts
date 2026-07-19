import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { UserDetailDrawerComponent } from './user-detail-drawer';
import { ConfigService } from '../../services/config.service';
import { AdminUserDetail } from '../../models/admin-user.model';
import { of, throwError } from 'rxjs';
import { ChangeDetectionStrategy } from '@angular/core';

function makeUser(overrides: Partial<AdminUserDetail> = {}): AdminUserDetail {
  return {
    id: 1,
    name: 'Test User',
    email: 'test@example.com',
    pictureUrl: null,
    googleId: 'gid123',
    role: 'TRIAL',
    trialEndDate: '2026-12-31T23:59:59Z',
    analysisCount: 5,
    lastLogin: '2026-07-01T10:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    isActive: true,
    deletedAt: null,
    emailCount: 10,
    reminderCount: 2,
    ...overrides,
  };
}

function getAnalysisCell(fixture: ComponentFixture<any>): string {
  const items = fixture.nativeElement.querySelectorAll('.meta-item');
  let result = '';
  items.forEach((item: any) => {
    const label = item.querySelector('.meta-label');
    if (label && label.textContent.trim() === 'Análisis usados') {
      result = item.querySelector('.meta-value')?.textContent?.trim() || '';
    }
  });
  return result;
}

describe('UserDetailDrawerComponent', () => {
  let component: UserDetailDrawerComponent;
  let fixture: ComponentFixture<UserDetailDrawerComponent>;
  let configServiceSpy: jasmine.SpyObj<ConfigService>;

  beforeEach(async () => {
    configServiceSpy = jasmine.createSpyObj('ConfigService', ['getEntry']);

    await TestBed.configureTestingModule({
      imports: [UserDetailDrawerComponent],
      providers: [
        { provide: ConfigService, useValue: configServiceSpy },
      ],
    })
    .overrideComponent(UserDetailDrawerComponent, {
      set: { changeDetection: ChangeDetectionStrategy.Default },
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserDetailDrawerComponent);
    component = fixture.componentInstance;
  });

  function openDrawer(user: AdminUserDetail): void {
    fixture.componentRef.setInput('user', user);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows analysisCount with default limit 20 when config not yet loaded', () => {
    configServiceSpy.getEntry.and.returnValue(of({} as any));
    openDrawer(makeUser({ analysisCount: 5 }));

    expect(getAnalysisCell(fixture)).toContain('5');
    expect(getAnalysisCell(fixture)).toContain('20');
  });

  it('shows loaded limit when ConfigService returns a value', () => {
    configServiceSpy.getEntry.and.returnValue(of({ key: 'TRIAL_ANALYSIS_LIMIT', value: '50' } as any));
    openDrawer(makeUser({ analysisCount: 3 }));

    expect(getAnalysisCell(fixture)).toContain('3');
    expect(getAnalysisCell(fixture)).toContain('50');
  });

  it('calls getEntry when drawer opens', () => {
    configServiceSpy.getEntry.and.returnValue(of({ key: 'TRIAL_ANALYSIS_LIMIT', value: '20' } as any));
    openDrawer(makeUser({ analysisCount: 0 }));

    expect(configServiceSpy.getEntry).toHaveBeenCalledWith('TRIAL_ANALYSIS_LIMIT');
  });

  it('keeps default 20 when ConfigService API fails', () => {
    configServiceSpy.getEntry.and.returnValue(throwError(() => new Error('server error')));
    openDrawer(makeUser({ analysisCount: 7 }));

    expect(getAnalysisCell(fixture)).toContain('7');
    expect(getAnalysisCell(fixture)).toContain('20');
  });

  it('does not call ConfigService when drawer is closed', () => {
    fixture.componentRef.setInput('user', makeUser());
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();

    expect(configServiceSpy.getEntry).not.toHaveBeenCalled();
  });

  it('shows updated limit when same drawer is reopened with new config value', () => {
    configServiceSpy.getEntry.and.returnValue(of({ key: 'TRIAL_ANALYSIS_LIMIT', value: '50' } as any));
    openDrawer(makeUser({ analysisCount: 2 }));
    expect(configServiceSpy.getEntry).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();

    configServiceSpy.getEntry.and.returnValue(of({ key: 'TRIAL_ANALYSIS_LIMIT', value: '100' } as any));
    openDrawer(makeUser({ analysisCount: 2 }));
    expect(configServiceSpy.getEntry).toHaveBeenCalledTimes(2);

    expect(getAnalysisCell(fixture)).toContain('100');
  });
});
