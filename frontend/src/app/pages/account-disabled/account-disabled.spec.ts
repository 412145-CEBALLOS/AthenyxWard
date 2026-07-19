import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountDisabledComponent } from './account-disabled';
import { RouterTestingModule } from '@angular/router/testing';

describe('AccountDisabledComponent', () => {
  let component: AccountDisabledComponent;
  let fixture: ComponentFixture<AccountDisabledComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountDisabledComponent, RouterTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountDisabledComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders title', () => {
    const title = fixture.nativeElement.querySelector('.disabled-title');
    expect(title.textContent).toContain('Tu cuenta ha sido desactivada');
  });

  it('renders support email link', () => {
    const link = fixture.nativeElement.querySelector('.support-link');
    expect(link.textContent).toContain('soporte@athenyxward.com');
    expect(link.getAttribute('href')).toContain('mailto:soporte@athenyxward.com');
  });

  it('navigates to /login on button click', () => {
    const router = TestBed.inject(RouterTestingModule as any);
    const navigateSpy = spyOn(component['router'], 'navigate');

    const button = fixture.nativeElement.querySelector('.login-btn');
    button.click();

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
