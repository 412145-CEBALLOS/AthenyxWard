import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  describe('browser platform', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [{ provide: PLATFORM_ID, useValue: 'browser' }],
      });
      service = TestBed.inject(ToastService);
    });

    it('starts with no toasts', () => {
      expect(service.toasts()).toEqual([]);
    });

    it('pushes a toast on error()', () => {
      service.error('Algo falló');
      const all = service.toasts();
      expect(all.length).toBe(1);
      expect(all[0].type).toBe('error');
      expect(all[0].message).toBe('Algo falló');
      expect(all[0].count).toBe(1);
    });

    it('stacks different messages', () => {
      service.error('Primero');
      service.error('Segundo');
      expect(service.toasts().length).toBe(2);
    });

    it('dedupes identical message within 3s by incrementing count', () => {
      service.error('Repetido');
      service.error('Repetido');
      service.error('Repetido');
      const all = service.toasts();
      expect(all.length).toBe(1);
      expect(all[0].count).toBe(3);
    });

    it('does not dedupe across different types', () => {
      service.error('Aviso');
      service.warning('Aviso');
      expect(service.toasts().length).toBe(2);
    });

    it('does not dedupe identical message after the dedupe window', fakeAsync(() => {
      service.error('Repetido');
      tick(3001);
      service.error('Repetido');
      const all = service.toasts();
      expect(all.length).toBe(2);
      expect(all[0].count).toBe(1);
      expect(all[1].count).toBe(1);
    }));

    it('auto-dismisses errors after 6s', fakeAsync(() => {
      service.error('Temporal');
      expect(service.toasts().length).toBe(1);
      tick(6000);
      expect(service.toasts().length).toBe(0);
    }));

    it('auto-dismisses info toasts after 4s', fakeAsync(() => {
      service.info('Hola');
      tick(4000);
      expect(service.toasts().length).toBe(0);
    }));

    it('honours a custom duration', fakeAsync(() => {
      service.error('Rápido', { duration: 1000 });
      tick(1000);
      expect(service.toasts().length).toBe(0);
    }));

    it('dismiss(id) removes a single toast', () => {
      service.error('A');
      service.error('B');
      const idA = service.toasts()[0].id;
      service.dismiss(idA);
      expect(service.toasts().length).toBe(1);
      expect(service.toasts()[0].message).toBe('B');
    });

    it('drops the oldest toast when the stack exceeds 5', () => {
      for (let i = 0; i < 6; i++) {
        service.error(`msg-${i}`);
      }
      const all = service.toasts();
      expect(all.length).toBe(5);
      expect(all[0].message).toBe('msg-1');
      expect(all[4].message).toBe('msg-5');
    });

    it('clear() removes every toast', () => {
      service.error('A');
      service.error('B');
      service.clear();
      expect(service.toasts().length).toBe(0);
    });
  });

  describe('server platform (SSR)', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [{ provide: PLATFORM_ID, useValue: 'server' }],
      });
      service = TestBed.inject(ToastService);
    });

    it('still appends to the signal but never schedules a timer', fakeAsync(() => {
      service.error('SSR error');
      expect(service.toasts().length).toBe(1);
      tick(10_000);
      expect(service.toasts().length).toBe(1);
    }));
  });
});
