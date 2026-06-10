import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { SettingsComponent } from './settings';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

describe('Settings', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let mockAuth: { user: ReturnType<typeof signal<{ accessibilityMode: boolean } | null>>; updateAccessibilityMode: jasmine.Spy };
  let mockToast: { error: jasmine.Spy };

  beforeEach(async () => {
    mockAuth = {
      user: signal<{ accessibilityMode: boolean } | null>({ accessibilityMode: true }),
      updateAccessibilityMode: jasmine.createSpy('updateAccessibilityMode').and.returnValue(of({})),
    };
    mockToast = { error: jasmine.createSpy('error') };

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuth },
        { provide: ToastService, useValue: mockToast },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('persists accessibility-mode toggle via the auth service', () => {
    component.onToggle('accessibility', false);
    expect(mockAuth.updateAccessibilityMode).toHaveBeenCalledWith(false);
  });

  it('rolls back the local accessibility flag and toasts when the API call fails', () => {
    mockAuth.updateAccessibilityMode.and.returnValue(throwError(() => new Error('boom')));
    component.onToggle('accessibility', false);
    expect(mockToast.error).toHaveBeenCalledWith('No se pudo guardar el ajuste de accesibilidad');
  });
});
