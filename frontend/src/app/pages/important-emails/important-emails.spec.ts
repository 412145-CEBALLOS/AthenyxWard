import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportantEmailsComponent } from './important-emails';

describe('ImportantEmails', () => {
  let component: ImportantEmailsComponent;
  let fixture: ComponentFixture<ImportantEmailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImportantEmailsComponent],
    })
    .compileComponents();

    fixture = TestBed.createComponent(ImportantEmailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
