import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FaqData } from '../models/faq.model';

@Injectable({
  providedIn: 'root',
})
export class HelpCenterService {
  private readonly http = inject(HttpClient);

  getFaqs(): Observable<FaqData> {
    return this.http.get<FaqData>('assets/help/faqs.es.json');
  }
}
