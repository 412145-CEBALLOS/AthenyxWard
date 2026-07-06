import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { take, toArray } from 'rxjs/operators';
import { EmailSearchService } from './email-search.service';
import { fakeAsync, tick } from '@angular/core/testing';

describe('EmailSearchService', () => {
  let service: EmailSearchService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EmailSearchService);
  });

  it('starts with an empty term', () => {
    expect(service.term()).toBe('');
  });

  it('set() updates the term signal and fires the debounced stream', fakeAsync(async () => {
    const seen: string[] = [];
    const sub = service.debouncedTerm$.subscribe((v) => seen.push(v));

    service.set('foo');
    expect(service.term()).toBe('foo');
    tick(300);
    expect(seen).toEqual(['foo']);

    sub.unsubscribe();
  }));

  it('debounces 300 ms — burst of keystrokes collapses into one emission', fakeAsync(() => {
    const seen: string[] = [];
    const sub = service.debouncedTerm$.subscribe((v) => seen.push(v));

    service.set('p');
    tick(100);
    service.set('pa');
    tick(100);
    service.set('pay');
    tick(300);
    expect(seen).toEqual(['pay']);

    sub.unsubscribe();
  }));

  it('distinctUntilChanged skips re-emissions of the same trimmed value', fakeAsync(() => {
    const seen: string[] = [];
    const sub = service.debouncedTerm$.subscribe((v) => seen.push(v));

    service.set('foo');
    tick(300);
    service.set('  foo  '); // same trimmed value
    tick(300);
    expect(seen).toEqual(['foo']);

    sub.unsubscribe();
  }));

  it('setTerm() updates the signal but does NOT fire the debounce', fakeAsync(() => {
    const seen: string[] = [];
    const sub = service.debouncedTerm$.subscribe((v) => seen.push(v));

    service.setTerm('deep-link');
    expect(service.term()).toBe('deep-link');
    tick(500);
    expect(seen).toEqual([]);

    sub.unsubscribe();
  }));

  it('clear() resets the term and fires the debounce', fakeAsync(() => {
    const seen: string[] = [];
    const sub = service.debouncedTerm$.subscribe((v) => seen.push(v));

    service.set('foo');
    tick(300);
    service.clear();
    tick(300);
    expect(seen).toEqual(['foo', '']);
    expect(service.term()).toBe('');

    sub.unsubscribe();
  }));

  it('open() / close() toggle isOpen', () => {
    expect(service.isOpen()).toBeFalse();
    service.open();
    expect(service.isOpen()).toBeTrue();
    service.close();
    expect(service.isOpen()).toBeFalse();
  });

  it('applyToInbox() emits immediately on inboxApply$ (no debounce)', fakeAsync(() => {
    const seen: string[] = [];
    const sub = service.inboxApply$.subscribe((v) => seen.push(v));

    service.applyToInbox('paypal');
    tick(0);
    expect(seen).toEqual(['paypal']);

    sub.unsubscribe();
  }));
});
