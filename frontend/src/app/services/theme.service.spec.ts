import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

const STORAGE_KEY = 'athenyx.theme';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  describe('init()', () => {
    it('defaults to auto when localStorage is empty', () => {
      service.init();
      expect(service.theme()).toBe('auto');
      expect(document.documentElement.getAttribute('data-theme')).toBeNull();
    });

    it('applies dark theme from localStorage', () => {
      localStorage.setItem(STORAGE_KEY, 'dark');
      service.init();
      expect(service.theme()).toBe('dark');
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('applies light theme from localStorage', () => {
      localStorage.setItem(STORAGE_KEY, 'light');
      service.init();
      expect(service.theme()).toBe('light');
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    });

    it('ignores invalid localStorage values and defaults to auto', () => {
      localStorage.setItem(STORAGE_KEY, 'purple');
      service.init();
      expect(service.theme()).toBe('auto');
      expect(document.documentElement.getAttribute('data-theme')).toBeNull();
    });
  });

  describe('setTheme()', () => {
    it('sets dark theme and writes localStorage', () => {
      service.setTheme('dark');
      expect(service.theme()).toBe('dark');
      expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('sets light theme and writes localStorage', () => {
      service.setTheme('light');
      expect(service.theme()).toBe('light');
      expect(localStorage.getItem(STORAGE_KEY)).toBe('light');
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    });

    it('sets auto and removes localStorage entry', () => {
      localStorage.setItem(STORAGE_KEY, 'dark');
      service.setTheme('auto');
      expect(service.theme()).toBe('auto');
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
      expect(document.documentElement.getAttribute('data-theme')).toBeNull();
    });

    it('overwrites previous localStorage value', () => {
      localStorage.setItem(STORAGE_KEY, 'light');
      service.setTheme('dark');
      expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
    });
  });
});
