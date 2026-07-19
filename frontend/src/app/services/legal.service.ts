import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ParsedLegal, parseLegalMarkdown } from '../utils/legal-md.util';
import { TERMS_CONTENT, PRIVACY_CONTENT } from '../utils/legal-text';

@Injectable({ providedIn: 'root' })
export class LegalService {
  private readonly terms = signal<ParsedLegal | null>(null);
  private readonly privacy = signal<ParsedLegal | null>(null);

  loadTerms(): Observable<ParsedLegal> {
    const parsed = parseLegalMarkdown(TERMS_CONTENT);
    this.terms.set(parsed);
    return of(parsed);
  }

  loadPrivacy(): Observable<ParsedLegal> {
    const parsed = parseLegalMarkdown(PRIVACY_CONTENT);
    this.privacy.set(parsed);
    return of(parsed);
  }

  readonly terms$ = this.terms.asReadonly();
  readonly privacy$ = this.privacy.asReadonly();
}
