import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const CONSUMER_EMAIL_PROVIDERS: ReadonlySet<string> = new Set([
  'gmail.com', 'googlemail.com',
  'outlook.com', 'outlook.es', 'hotmail.com', 'hotmail.es', 'live.com', 'live.es', 'msn.com',
  'yahoo.com', 'yahoo.es', 'ymail.com',
  'icloud.com', 'me.com', 'mac.com',
  'aol.com',
  'protonmail.com', 'proton.me', 'pm.me',
  'gmx.com', 'gmx.es', 'gmx.net',
  'mail.com',
  'zoho.com',
  'fastmail.com', 'fastmail.fm',
  'tutanota.com', 'tutanota.de', 'tuta.io',
  'yandex.com', 'yandex.ru',
]);

/** Resolution strategy chosen by {@link AvatarService.getState}. */
export type AvatarKind = 'gravatar' | 'favicon' | 'initials';

/**
 * Aggregated view of how a sender's avatar should be rendered.
 * Returned by {@link AvatarService.getState} — components consume this
 * directly.
 */
export interface AvatarState {
  kind: AvatarKind;
  gravatarUrl: string;
  faviconUrl: string;
  initials: string;
  hue: number;
}

/**
 * Derives 1–2 character initials from a display name. Returns
 * {@code "?"} for falsy input.
 */
export function getInitials(name: string): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
}

/**
 * Stable 0–360 hue derived from the input string, used as a fallback
 * background colour for the initials avatar.
 */
export function hashHue(input: string): number {
  if (!input) return 220;
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) - hash + input.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % 360;
}

/**
 * Picks how to render a sender's avatar: Gravatar (only for known
 * consumer email providers), the sender domain's favicon, or coloured
 * initials as a last resort.
 *
 * <p>Failed Gravatar or favicon lookups are remembered in signals so
 * subsequent views skip the failing source and fall back automatically.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class AvatarService {
  private readonly platformId = inject(PLATFORM_ID);

  private readonly gravatarHashes = signal<ReadonlyMap<string, string>>(new Map());
  private readonly hashingInFlight = new Set<string>();
  private readonly failedGravatars = signal<ReadonlySet<string>>(new Set());
  private readonly failedFavicons = signal<ReadonlySet<string>>(new Set());

  /**
   * Eagerly computes SHA-256 hashes for the supplied senders, so the
   * first list render can render Gravatar URLs without flashing the
   * initials fallback.
   *
   * @param senders email addresses (or nullish) to pre-hash
   */
  precompute(senders: ReadonlyArray<string | null | undefined>): void {
    for (const sender of senders) {
      const email = this.normalize(sender);
      if (email && this.isConsumerEmailProvider(email)) {
        this.computeHash(email);
      }
    }
  }

  /**
   * Returns the full {@link AvatarState} the template needs to render
   * the avatar (which kind, which URL, what initials, what colour).
   *
   * @param sender     sender email
   * @param senderName sender display name (used for the initials fallback)
   */
  getState(sender: string | null | undefined, senderName?: string | null): AvatarState {
    const email = this.normalize(sender);
    const displayName = senderName?.trim() || sender || '';
    return {
      kind: this.pickKind(email),
      gravatarUrl: this.buildGravatarUrl(email),
      faviconUrl: this.buildFaviconUrl(email),
      initials: getInitials(displayName),
      hue: hashHue(email || displayName),
    };
  }

  /**
   * Marks a Gravatar or favicon URL as failed for the given sender so
   * future lookups skip that source.
   */
  markFailed(sender: string | null | undefined, kind: 'gravatar' | 'favicon'): void {
    const email = this.normalize(sender);
    if (!email) return;
    if (kind === 'gravatar') {
      this.failedGravatars.update((set) => set.has(email) ? set : new Set(set).add(email));
    } else {
      const domain = this.getDomain(email);
      if (!domain) return;
      this.failedFavicons.update((set) => set.has(domain) ? set : new Set(set).add(domain));
    }
  }

  private pickKind(email: string): AvatarKind {
    if (!email) return 'initials';
    if (this.failedGravatars().has(email)) {
      const domain = this.getDomain(email);
      if (!domain || this.failedFavicons().has(domain)) return 'initials';
      return 'favicon';
    }
    if (this.gravatarHashes().has(email)) return 'gravatar';
    const domain = this.getDomain(email);
    if (domain && !this.failedFavicons().has(domain)) return 'favicon';
    return 'initials';
  }

  private buildGravatarUrl(email: string): string {
    if (!this.isConsumerEmailProvider(email) || !email) return '';
    const hash = this.gravatarHashes().get(email);
    if (!hash) {
      this.computeHash(email);
      return '';
    }
    return `https://www.gravatar.com/avatar/${hash}?s=128&d=404&r=g`;
  }

  private buildFaviconUrl(email: string): string {
    const domain = this.getDomain(email);
    if (!domain || this.failedFavicons().has(domain)) return '';
    return `https://${domain}/favicon.ico`;
  }

  private computeHash(email: string): void {
    if (!email || this.gravatarHashes().has(email) || this.hashingInFlight.has(email)) return;
    if (!isPlatformBrowser(this.platformId)) return;
    if (typeof crypto === 'undefined' || !crypto.subtle) return;
    this.hashingInFlight.add(email);
    const data = new TextEncoder().encode(email);
    crypto.subtle.digest('SHA-256', data)
      .then((buffer) => {
        const hex = Array.from(new Uint8Array(buffer))
          .map((b) => b.toString(16).padStart(2, '0'))
          .join('');
        this.gravatarHashes.update((map) => map.has(email) ? map : new Map(map).set(email, hex));
      })
      .catch(() => {})
      .finally(() => {
        this.hashingInFlight.delete(email);
      });
  }

  private isConsumerEmailProvider(email: string): boolean {
    const domain = this.getDomain(email);
    return !!domain && CONSUMER_EMAIL_PROVIDERS.has(domain);
  }

  private getDomain(email: string): string {
    if (!email) return '';
    const at = email.lastIndexOf('@');
    if (at < 0) return '';
    return email.substring(at + 1).trim().toLowerCase();
  }

  private normalize(sender: string | null | undefined): string {
    if (!sender) return '';
    return sender.trim().toLowerCase();
  }
}
