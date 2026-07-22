import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PopupService } from '../../services/popup.service';

import { PlanComponent } from './plan';

describe('Plan', () => {
  let component: PlanComponent;
  let fixture: ComponentFixture<PlanComponent>;
  let mockPopupService: jasmine.SpyObj<PopupService>;

  beforeEach(async () => {
    mockPopupService = jasmine.createSpyObj('PopupService', ['setPopup', 'getPopup', 'closePopup']);

    await TestBed.configureTestingModule({
      imports: [PlanComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PopupService, useValue: mockPopupService },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(PlanComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
