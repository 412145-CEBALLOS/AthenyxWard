import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { LayoutComponent } from './layout';
import { AuthService } from '../../services/auth.service';

describe('Layout', () => {
  let component: LayoutComponent;
  let fixture: ComponentFixture<LayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LayoutComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            user: signal(null),
            logout: () => undefined,
            logoutAll: () => undefined,
          },
        },
      ],
    })
    .overrideComponent(LayoutComponent, {
      set: { template: '<div class="layout-stub"></div>' },
    })
    .compileComponents();

    fixture = TestBed.createComponent(LayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens the sidebar on the first toggleSidebar() call', () => {
    expect(component.sidebarOpen).toBeFalse();
    component.toggleSidebar();
    expect(component.sidebarOpen).toBeTrue();
  });

  it('marks the sidebar as closing on the second toggleSidebar() call', fakeAsync(() => {
    component.toggleSidebar();
    component.toggleSidebar();
    expect(component.sidebarOpen).toBeTrue();
    expect(component.sidebarClosing).toBeTrue();
    tick(400);
    expect(component.sidebarOpen).toBeFalse();
    expect(component.sidebarClosing).toBeFalse();
  }));
});
