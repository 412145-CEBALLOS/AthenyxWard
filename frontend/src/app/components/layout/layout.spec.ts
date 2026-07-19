import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LayoutComponent } from './layout';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { signal, PLATFORM_ID, Signal, WritableSignal } from '@angular/core';
import { ChangeDetectionStrategy } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

class MockAppConfigInitializerService {
  readonly supportEmail: Signal<string> = signal('support@athenyxward.com').asReadonly();
  readonly pollIntervalSeconds: WritableSignal<number> = signal(120);
  readonly loading: Signal<boolean> = signal(false).asReadonly();
  load = jasmine.createSpy('load');
}

describe('LayoutComponent', () => {
  let component: LayoutComponent;
  let fixture: ComponentFixture<LayoutComponent>;
  let mockAppConfig: MockAppConfigInitializerService;

  beforeEach(async () => {
    mockAppConfig = new MockAppConfigInitializerService();

    await TestBed.configureTestingModule({
      imports: [LayoutComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: AppConfigInitializerService, useValue: mockAppConfig },
      ],
    })
      .overrideComponent(LayoutComponent, {
        set: { changeDetection: ChangeDetectionStrategy.Default },
      })
      .compileComponents();

    fixture = TestBed.createComponent(LayoutComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('calls appConfig.load() on init', () => {
    component.ngOnInit();
    expect(mockAppConfig.load).toHaveBeenCalled();
  });
});
