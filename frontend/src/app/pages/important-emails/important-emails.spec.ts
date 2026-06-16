import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ImportantEmailsComponent } from './important-emails';
import { EmailService } from '../../services/email.service';

describe('ImportantEmailsComponent', () => {
  let component: ImportantEmailsComponent;
  let fixture: ComponentFixture<ImportantEmailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImportantEmailsComponent, RouterTestingModule, HttpClientTestingModule],
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
