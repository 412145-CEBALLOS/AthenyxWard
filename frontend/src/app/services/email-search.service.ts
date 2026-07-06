import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';

/**
 * Shared state for the email search box. The {@link HeaderComponent}
 * writes the raw input value here; the home page (and any future
 * consumer) subscribes to {@link debouncedTerm$} to react to changes
 * after the 300 ms debounce window.
 *
 * <p>Splitting {@link set} (user input — fires the debounce) from
 * {@link setTerm} (programmatic / URL sync — does NOT fire the
 * debounce) lets the home page reflect deep-link values into the
 * search input without triggering a second fetch on top of the one
 * it already kicked off synchronously.</p>
 *
 * <p>On desktop the search bar drives a dropdown of top results
 * (rendered by {@code EmailSearchResultsComponent} mounted inside
 * the header) instead of filtering the inbox inline. The inbox
 * updates only when the user explicitly opts in via Enter or the
 * "Ver todos los resultados" link — both go through
 * {@link applyToInbox}, which fires {@link inboxApply$} for the
 * home page to react to.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class EmailSearchService {
  /**
   * Current text in the search input. Bound to the {@code <input>}
   * in the header via {@code [value]}. Updated synchronously on
   * every keystroke so the clear button and the URL-sync round-trip
   * stay responsive.
   */
  readonly term = signal('');

  /**
   * Whether the search dropdown should be visible. Flipped to
   * {@code true} on the input's {@code focus} event, back to
   * {@code false} on blur / Escape / result-click. The header uses
   * a short blur-delay so a click on a result registers before
   * the dropdown closes.
   */
  readonly isOpen = signal(false);

  private readonly term$ = new Subject<string>();
  private readonly applyToInbox$ = new Subject<string>();

  /**
   * Emits the trimmed search term at most once per 300 ms of
   * silence. {@code distinctUntilChanged} drops no-op re-runs (e.g.
   * trailing-whitespace toggles, repeated identical keystrokes).
   *
   * <p>Consumed by the home page via {@code switchMap} so a new
   * value cancels the in-flight fetch for the previous term —
   * preventing the race where the older (slower) response overwrites
   * the newer one. Also consumed by the
   * {@code EmailSearchResultsComponent} for the desktop dropdown.</p>
   */
  readonly debouncedTerm$ = this.term$.pipe(
    debounceTime(300),
    map((t) => t.trim()),
    distinctUntilChanged(),
  );

  /**
   * Emitted when the user explicitly asks to apply the active search
   * to the inbox: pressing Enter on the search input, or clicking
   * "Ver todos los resultados" in the desktop dropdown. Consumed by
   * the home page to force-fetch the inbox (immediate, no debounce).
   */
  readonly inboxApply$: Observable<string> = this.applyToInbox$.asObservable();

  /**
   * User-driven update (header {@code (input)} event). Updates the
   * signal AND fires the debounce so the dropdown / inbox react.
   */
  set(value: string): void {
    this.term.set(value);
    this.term$.next(value);
  }

  /**
   * Programmatic update (URL sync, deep link, back/forward). Updates
   * the signal so the input reflects the new value, but does NOT
   * fire the debounce — the caller is expected to trigger the
   * matching fetch itself.
   */
  setTerm(value: string): void {
    this.term.set(value);
  }

  /** Convenience for the clear button. */
  clear(): void {
    this.set('');
  }

  /** Show the desktop dropdown. Called on the search input's focus. */
  open(): void {
    this.isOpen.set(true);
  }

  /** Hide the desktop dropdown. Called on blur / Escape / click-outside. */
  close(): void {
    this.isOpen.set(false);
  }

  /**
   * Force the inbox to apply the given search term. The caller is
   * expected to {@link close} the dropdown at the same time (e.g.
   * on Enter or on "Ver todos").
   */
  applyToInbox(term: string): void {
    this.applyToInbox$.next(term);
  }
}
