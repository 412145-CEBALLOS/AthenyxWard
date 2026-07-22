import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class PopupService {
  private popup: Window | null = null;

  setPopup(popup: Window | null): void {
    this.popup = popup;
  }

  getPopup(): Window | null {
    return this.popup;
  }

  closePopup(): void {
    if (this.popup && !this.popup.closed) {
      this.popup.close();
    }
    this.popup = null;
  }
}
