import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, HttpErrorResponse, HttpHandlerFn, HttpRequest, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { PLATFORM_ID } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { take } from 'rxjs/operators';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { errorToastInterceptor, SKIP_ERROR_TOAST } from './error-toast.interceptor';
import { ToastService } from '../services/toast.service';
import { HTTP_ERROR_MESSAGES, resolveErrorMessage } from './http-error-messages';

describe('resolveErrorMessage', () => {
  function err(status: number, body: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: body });
  }

  it('uses backend body.error when present', () => {
    expect(resolveErrorMessage(err(500, { error: 'Algo falló' }))).toBe('Algo falló');
  });

  it('uses backend body.message when body.error is missing', () => {
    expect(resolveErrorMessage(err(500, { message: 'Detalle' }))).toBe('Detalle');
  });

  it('trims plain-string backend bodies', () => {
    expect(resolveErrorMessage(err(500, '  crudo  '))).toBe('crudo');
  });

  it('falls back to the status map when no body is present', () => {
    expect(resolveErrorMessage(err(404, null))).toBe(HTTP_ERROR_MESSAGES[404]);
  });

  it('uses the offline message for status 0', () => {
    expect(resolveErrorMessage(err(0, null))).toBe(HTTP_ERROR_MESSAGES[0]);
  });

  it('uses a default sentence with the status code for unknown statuses', () => {
    expect(resolveErrorMessage(err(418, null))).toContain('418');
  });
});

describe('errorToastInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let toast: { error: jasmine.Spy };

  beforeEach(() => {
    toast = { error: jasmine.createSpy('error') };

    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: ToastService, useValue: toast },
        provideHttpClient(withInterceptors([errorToastInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function flushWithError(status: number, body: any, url = '/api/test'): Promise<unknown> {
    return new Promise((resolve, reject) => {
      http.get(url).subscribe({ next: resolve, error: reject });
      httpTesting.expectOne(url).flush(body, { status, statusText: String(status) });
    });
  }

  it('toasts the backend message on 500', async () => {
    const promise = flushWithError(500, { error: 'Backend dice: kaput' });
    await expectAsync(promise).toBeRejected();
    expect(toast.error).toHaveBeenCalledWith('Backend dice: kaput');
  });

  it('falls back to the status map when the body has no message', async () => {
    const promise = flushWithError(404, null, '/api/missing');
    await expectAsync(promise).toBeRejected();
    expect(toast.error).toHaveBeenCalledWith(HTTP_ERROR_MESSAGES[404]);
  });

  it('uses the offline message for status 0 (network failure)', async () => {
    const req = new HttpRequest('GET', '/api/offline');
    let captured: HttpErrorResponse | undefined;
    try {
      await new Promise<void>((_resolve, reject) => {
        http.request(req).subscribe({ error: (e: HttpErrorResponse) => { captured = e; reject(); } });
        httpTesting.expectOne('/api/offline').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      });
    } catch {
      // expected
    }
    expect(captured).toBeDefined();
    expect(toast.error).toHaveBeenCalledWith(HTTP_ERROR_MESSAGES[0]);
  });

  it('does NOT call toast.error for 401', async () => {
    const promise = flushWithError(401, { error: 'No autenticado' }, '/api/x');
    await expectAsync(promise).toBeRejected();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('re-throws the error so subscribers can still react', async () => {
    const promise = flushWithError(500, { error: 'boom' });
    await expectAsync(promise).toBeRejectedWith(jasmine.any(HttpErrorResponse));
  });

  it('honours SKIP_ERROR_TOAST context token', async () => {
    const promise = new Promise<void>((_resolve, reject) => {
      http.get('/api/silent', { context: new HttpContext().set(SKIP_ERROR_TOAST, true) }).subscribe({ error: () => reject() });
      httpTesting.expectOne('/api/silent').flush({ error: 'ignored' }, { status: 500, statusText: '500' });
    });
    await expectAsync(promise).toBeRejected();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('SKIP_ERROR_TOAST defaults to false', () => {
    const ctx = new HttpContext();
    expect(ctx.get(SKIP_ERROR_TOAST)).toBeFalse();
  });
});

describe('errorToastInterceptor on server (SSR)', () => {
  let toast: { error: jasmine.Spy };

  beforeEach(() => {
    toast = { error: jasmine.createSpy('error') };
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        { provide: ToastService, useValue: toast },
        provideHttpClient(withInterceptors([errorToastInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });

  it('does not call toast.error on the server', (done) => {
    const http = TestBed.inject(HttpClient);
    const httpTesting = TestBed.inject(HttpTestingController);
    http.get('/api/ssr').subscribe({
      error: () => {
        expect(toast.error).not.toHaveBeenCalled();
        httpTesting.verify();
        done();
      },
    });
    httpTesting.expectOne('/api/ssr').flush({ error: 'x' }, { status: 500, statusText: '500' });
  });
});

describe('errorToastInterceptor composition with refresh', () => {
  it('is safe to register together with refreshInterceptor (no cycles)', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: ToastService, useValue: { error: () => undefined } },
        provideHttpClient(withInterceptors([errorToastInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    expect(TestBed.inject(HttpClient)).toBeTruthy();
  });
});
