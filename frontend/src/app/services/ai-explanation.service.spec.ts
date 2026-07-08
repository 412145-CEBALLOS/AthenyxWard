import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AiExplanationService } from './ai-explanation.service';
import { AiExplanation } from '../models/ai-explanation.model';

function makeExplanation(overrides: Partial<AiExplanation> = {}): AiExplanation {
  return {
    id: 1,
    text: 'Este correo presenta indicadores de phishing.',
    origin: 'LLM',
    modelName: 'llama3',
    generatedAt: '2026-07-07T10:00:00Z',
    ...overrides,
  };
}

describe('AiExplanationService', () => {
  let service: AiExplanationService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        AiExplanationService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    service = TestBed.inject(AiExplanationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts to /api/emails/{id}/explain', () => {
    service.explain(42).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/emails/42/explain');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(makeExplanation());
  });

  it('returns AiExplanation on 200', () => {
    const expected = makeExplanation({ origin: 'LLM', modelName: 'llama3' });
    service.explain(1).subscribe((result) => {
      expect(result).toEqual(expected);
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.flush(expected);
  });

  it('passes through 403 as HttpErrorResponse', () => {
    service.explain(1).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(403);
      },
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
  });

  it('passes through 503 as HttpErrorResponse', () => {
    service.explain(1).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(503);
      },
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.flush('AI unavailable', { status: 503, statusText: 'Service Unavailable' });
  });

  it('passes through 500 as HttpErrorResponse', () => {
    service.explain(1).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(500);
      },
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.flush('Internal error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('handles timeout error (0 status)', () => {
    service.explain(1).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(0);
      },
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.error(new ProgressEvent('error'), { status: 0, statusText: '' });
  });

  it('maps FALLBACK origin correctly', () => {
    const fallback = makeExplanation({ origin: 'FALLBACK', modelName: 'llama3' });
    service.explain(1).subscribe((result) => {
      expect(result.origin).toBe('FALLBACK');
    });
    const req = httpMock.expectOne('/api/emails/1/explain');
    req.flush(fallback);
  });
});
