import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HelpCenterComponent } from './help-center';

describe('HelpCenterComponent', () => {
  let component: HelpCenterComponent;
  let fixture: ComponentFixture<HelpCenterComponent>;
  let httpMock: HttpTestingController;

  const mockFaqData = {
    categories: [
      {
        id: 'primeros-pasos',
        name: 'Primeros pasos',
        icon: 'ti ti-rocket',
        items: [
          { q: '¿Qué es Athenyx Ward?', a: 'Un asistente de seguridad para tu correo.' },
          { q: '¿Cómo conecto mi cuenta?', a: 'Mediante OAuth2 de Google.' },
        ],
      },
      {
        id: 'seguridad',
        name: 'Seguridad y privacidad',
        icon: 'ti ti-shield-lock',
        items: [
          { q: '¿Qué datos almacenáis?', a: 'Solo datos relevantes para el análisis.' },
        ],
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HelpCenterComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(HelpCenterComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    fixture.destroy();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('makes a GET request to assets/help/faqs.es.json on init', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === 'assets/help/faqs.es.json');
    expect(req.request.method).toBe('GET');
    req.flush(mockFaqData);
  });

  it('renders categories and items after successful load', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-category').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(3);
    expect(fixture.nativeElement.querySelector('.help-category').textContent).toContain('Primeros pasos');
  });

  it('renders error state when HTTP request fails', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush('server error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.help-state-error')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(0);
  });

  it('filters items by question text (case-insensitive)', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('OAuth');
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.help-item');
    expect(items.length).toBe(1);
    expect(items[0].querySelector('.help-answer').textContent).toContain('OAuth2');
  });

  it('filters items by answer text', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('datos');
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.help-item');
    expect(items.length).toBe(1);
    expect(items[0].querySelector('.help-answer').textContent).toContain('datos');
  });

  it('hides categories with no matching items', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('OAuth');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-category').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.help-category').textContent).toContain('Primeros pasos');
  });

  it('shows no-results state with mailto link when query has no matches', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('xyz123nodematch');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.help-state-title').textContent).toContain('xyz123nodematch');
    const mailtoLink = fixture.nativeElement.querySelector('.help-support-link') as HTMLAnchorElement;
    expect(mailtoLink.href).toContain('mailto:soporte@athenyxward.com');
    expect(mailtoLink.href).toContain('Consulta%20desde%20Centro%20de%20Ayuda');
  });

  it('keeps the search input visible in the no-results state', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('xyz123nodematch');
    fixture.detectChanges();

    const searchInput = fixture.nativeElement.querySelector('.help-search-input') as HTMLInputElement;
    expect(searchInput).toBeTruthy();
    expect(searchInput.type).toBe('text');
    expect(searchInput.value).toBe('xyz123nodematch');
  });

  it('clears query and restores categories when clear button is clicked in no-results state', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('xyz123nodematch');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.help-state-title')).toBeTruthy();

    const clearBtn = fixture.nativeElement.querySelector('.help-search-clear');
    clearBtn.click();
    fixture.detectChanges();

    expect(component.query()).toBe('');
    expect(fixture.nativeElement.querySelectorAll('.help-category').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.help-state-title')).toBeFalsy();
  });

  it('clears query when clear button is clicked', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('OAuth');
    fixture.detectChanges();

    const clearBtn = fixture.nativeElement.querySelector('.help-search-clear');
    clearBtn.click();
    fixture.detectChanges();

    expect(component.query()).toBe('');
  });

  it('search input is case-insensitive', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('OAUTH');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(1);
  });

  it('renders 9 questions across 4 categories', () => {
    const fullFaqData = {
      categories: [
        {
          id: 'primeros-pasos',
          name: 'Primeros pasos',
          icon: 'ti ti-rocket',
          items: [
            { q: 'Pregunta 1', a: 'Respuesta 1' },
            { q: 'Pregunta 2', a: 'Respuesta 2' },
            { q: 'Pregunta 3', a: 'Respuesta 3' },
          ],
        },
        {
          id: 'seguridad',
          name: 'Seguridad y privacidad',
          icon: 'ti ti-shield-lock',
          items: [
            { q: 'Pregunta 4', a: 'Respuesta 4' },
            { q: 'Pregunta 5', a: 'Respuesta 5' },
          ],
        },
        {
          id: 'planes',
          name: 'Planes y facturación',
          icon: 'ti ti-credit-card',
          items: [
            { q: 'Pregunta 6', a: 'Respuesta 6' },
            { q: 'Pregunta 7', a: 'Respuesta 7' },
          ],
        },
        {
          id: 'analisis',
          name: 'Análisis e IA',
          icon: 'ti ti-brain',
          items: [
            { q: 'Pregunta 8', a: 'Respuesta 8' },
            { q: 'Pregunta 9', a: 'Respuesta 9' },
          ],
        },
      ],
    };

    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(fullFaqData);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-category').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(9);
  });

  it('matches items with accents when query is without accents', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('Athenyx');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.help-answer').textContent).toContain('seguridad');
  });

  it('matches items with accents when query has different capitalization and accents', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('OAuth2');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(1);
  });

  it('does not break when query has only special characters', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('assets/help/faqs.es.json');
    req.flush(mockFaqData);
    fixture.detectChanges();

    component.query.set('áéíóú');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.help-item').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.help-state-title').textContent).toContain('áéíóú');
  });
});
