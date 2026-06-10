import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAuditComponent } from './admin-audit';

describe('AdminAudit', () => {
  let component: AdminAuditComponent;
  let fixture: ComponentFixture<AdminAuditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAuditComponent],
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminAuditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
