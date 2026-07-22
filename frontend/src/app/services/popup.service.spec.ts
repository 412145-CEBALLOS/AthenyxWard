import { PopupService } from './popup.service';

describe('PopupService', () => {
  let service: PopupService;

  beforeEach(() => {
    service = new PopupService();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('setPopup', () => {
    it('stores the popup reference', () => {
      const mockWindow = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      expect(service.getPopup()).toBe(mockWindow);
    });

    it('overwrites previous popup reference', () => {
      const mockWindow1 = { closed: false, close: jasmine.createSpy('close') } as any;
      const mockWindow2 = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow1);
      service.setPopup(mockWindow2);
      expect(service.getPopup()).toBe(mockWindow2);
    });

    it('can set null', () => {
      const mockWindow = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      service.setPopup(null);
      expect(service.getPopup()).toBeNull();
    });
  });

  describe('getPopup', () => {
    it('returns null initially', () => {
      expect(service.getPopup()).toBeNull();
    });

    it('returns the stored popup', () => {
      const mockWindow = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      expect(service.getPopup()).toBe(mockWindow);
    });
  });

  describe('closePopup', () => {
    it('closes the popup if it is open', () => {
      const mockWindow = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      service.closePopup();
      expect(mockWindow.close).toHaveBeenCalled();
    });

    it('does not call close if popup is already closed', () => {
      const mockWindow = { closed: true, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      service.closePopup();
      expect(mockWindow.close).not.toHaveBeenCalled();
    });

    it('does nothing if no popup is stored', () => {
      expect(() => service.closePopup()).not.toThrow();
    });

    it('clears the stored popup reference', () => {
      const mockWindow = { closed: false, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      service.closePopup();
      expect(service.getPopup()).toBeNull();
    });

    it('clears reference even if popup was already closed', () => {
      const mockWindow = { closed: true, close: jasmine.createSpy('close') } as any;
      service.setPopup(mockWindow);
      service.closePopup();
      expect(service.getPopup()).toBeNull();
    });
  });
});
