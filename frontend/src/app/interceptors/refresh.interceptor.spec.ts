import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest, HttpHandlerFn, HttpResponse, HttpEvent } from '@angular/common/http';
import { PLATFORM_ID } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, throwError, Subject } from 'rxjs';
import { take } from 'rxjs/operators';

import { refreshInterceptor } from './refresh.interceptor';
import { AuthService } from '../services/auth.service';

describe('refreshInterceptor', () => {
  const REFRESH_PATH = '/api/auth/refresh';

  function makeHandler(result$: Observable<HttpEvent<unknown>>): jasmine.Spy<HttpHandlerFn> {
    return jasmine.createSpy('next').and.returnValue(result$) as jasmine.Spy<HttpHandlerFn>;
  }

  function runInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn) {
    return TestBed.runInInjectionContext(() => refreshInterceptor(req, next));
  }

  function setup(platform: 'browser' | 'server', auth: Partial<AuthService>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: platform },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
      ],
    });
  }

  describe('platform/URL guards', () => {
    it('passes through on the server (SSR)', (done: DoneFn) => {
      setup('server', { refresh: jasmine.createSpy('refresh') } as unknown as AuthService);
      const next = makeHandler(of(new HttpResponse({ status: 200 })));
      runInterceptor(new HttpRequest('GET', '/api/emails/fetch'), next)
        .pipe(take(1))
        .subscribe({
          next: () => {
            expect(next).toHaveBeenCalledTimes(1);
            done();
          },
          error: done.fail,
        });
    });

    it('forwards errors unchanged on the server', (done: DoneFn) => {
      setup('server', { refresh: jasmine.createSpy('refresh') } as unknown as AuthService);
      const err = new HttpErrorResponse({ status: 500 });
      const next = makeHandler(throwError(() => err));
      runInterceptor(new HttpRequest('GET', '/api/emails/fetch'), next)
        .pipe(take(1))
        .subscribe({
          error: (e: HttpErrorResponse) => {
            expect(e.status).toBe(500);
            expect(next).toHaveBeenCalledTimes(1);
            done();
          },
        });
    });

    it('does not trigger refresh for /api/auth/refresh itself', (done: DoneFn) => {
      const refreshSpy = jasmine
        .createSpy('refresh')
        .and.returnValue(of({ accessToken: 'x', expiresIn: 1 }));
      setup('browser', { refresh: refreshSpy } as unknown as AuthService);
      const next = makeHandler(of(new HttpResponse({ status: 401 })));
      runInterceptor(new HttpRequest('POST', REFRESH_PATH, null), next)
        .pipe(take(1))
        .subscribe({
          next: (event) => {
            expect(event).toBeTruthy();
            expect(next).toHaveBeenCalledTimes(1);
            expect(refreshSpy).not.toHaveBeenCalled();
            done();
          },
          error: done.fail,
        });
    });

    it('does not trigger refresh for the logout endpoint', (done: DoneFn) => {
      const refreshSpy = jasmine.createSpy('refresh');
      setup('browser', { refresh: refreshSpy } as unknown as AuthService);
      const next = makeHandler(of(new HttpResponse({ status: 200 })));
      runInterceptor(new HttpRequest('POST', '/api/auth/logout', null), next)
        .pipe(take(1))
        .subscribe({
          next: () => {
            expect(next).toHaveBeenCalledTimes(1);
            expect(refreshSpy).not.toHaveBeenCalled();
            done();
          },
          error: done.fail,
        });
    });
  });

  describe('behaviour on the browser', () => {
    it('does not refresh on non-401 errors', (done: DoneFn) => {
      const refreshSpy = jasmine.createSpy('refresh');
      setup('browser', { refresh: refreshSpy } as unknown as AuthService);
      const handler = makeHandler(throwError(() => new HttpErrorResponse({ status: 500 })));
      runInterceptor(new HttpRequest('GET', '/api/emails/fetch'), handler)
        .pipe(take(1))
        .subscribe({
          error: (err: HttpErrorResponse) => {
            expect(err.status).toBe(500);
            expect(refreshSpy).not.toHaveBeenCalled();
            done();
          },
        });
    });

    it('calls refresh on a 401, then replays the original request', (done: DoneFn) => {
      const refreshSpy = jasmine
        .createSpy('refresh')
        .and.returnValue(of({ accessToken: 'new', expiresIn: 900 }));
      setup('browser', { refresh: refreshSpy } as unknown as AuthService);

      let handlerCall = 0;
      const handler = jasmine
        .createSpy('next')
        .and.callFake(() => {
          handlerCall++;
          return handlerCall === 1
            ? throwError(() => new HttpErrorResponse({ status: 401 }))
            : of(new HttpResponse({ status: 200 }));
        }) as jasmine.Spy<HttpHandlerFn>;

      runInterceptor(new HttpRequest('GET', '/api/emails/fetch'), handler)
        .pipe(take(1))
        .subscribe({
          next: (event) => {
            expect(event).toBeTruthy();
            expect(refreshSpy).toHaveBeenCalledTimes(1);
            expect(handler).toHaveBeenCalledTimes(2);
            done();
          },
          error: done.fail,
        });
    });

  });
});
