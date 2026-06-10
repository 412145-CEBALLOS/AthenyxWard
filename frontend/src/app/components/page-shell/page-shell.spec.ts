import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PageShellComponent } from './page-shell';

describe('PageShell', () => {
  let component: PageShellComponent;
  let fixture: ComponentFixture<PageShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PageShellComponent],
    })
    .overrideComponent(PageShellComponent, {
      set: { template: '' },
    })
    .compileComponents();

    fixture = TestBed.createComponent(PageShellComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('icon', 'ti ti-home');
    fixture.componentRef.setInput('title', 'Test');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
