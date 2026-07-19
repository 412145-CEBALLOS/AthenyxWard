import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { LegalService } from '../../services/legal.service';
import { AuthService } from '../../services/auth.service';
import { LegalBlock } from '../../utils/legal-md.util';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [PageShellComponent, RouterLink],
  templateUrl: './legal.html',
  styleUrl: './legal.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalComponent implements OnInit, OnDestroy {
  private readonly legalService = inject(LegalService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly slug = signal<string>('terms');
  readonly fromOAuth = signal(false);
  readonly nextUrl = signal<string>('/home');
  readonly hasReadEnough = signal(false);
  readonly loading = signal(true);
  readonly error = signal(false);

  private scrollListener: (() => void) | null = null;

  readonly document = computed(() => {
    const s = this.slug();
    return s === 'privacy' ? this.legalService.privacy$() : this.legalService.terms$();
  });

  readonly isPrivacy = computed(() => this.slug() === 'privacy');

  readonly pageTitle = computed(() => this.isPrivacy() ? 'Política de Privacidad' : 'Términos y Condiciones');

  readonly pageSubtitle = computed(() => {
    const doc = this.document();
    if (!doc) return '';
    const date = doc.meta.date ? new Date(doc.meta.date + 'T00:00:00').toLocaleDateString('es-ES', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'Europe/Madrid' }) : '';
    return doc.meta.version ? `Versión ${doc.meta.version}${date ? ' · ' + date : ''}` : date;
  });

  readonly blocks = computed<LegalBlock[]>(() => this.document()?.blocks ?? []);

  readonly canAccept = computed(() => this.fromOAuth() && this.hasReadEnough());

  ngOnInit(): void {
    const slug = this.router.url.split('/').pop() ?? 'terms';
    this.slug.set(slug);
    this.fromOAuth.set(this.route.snapshot.queryParamMap.get('from') === 'oauth');
    this.nextUrl.set(this.route.snapshot.queryParamMap.get('next') ?? '/home');

    const loader = slug === 'privacy'
      ? this.legalService.loadPrivacy()
      : this.legalService.loadTerms();

    loader.subscribe({
      next: () => {
        this.loading.set(false);
        this.setupScrollGate();
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  private setupScrollGate(): void {
    if (typeof window === 'undefined') return;
    const el = document.documentElement;
    const check = () => {
      const scrollable = el.scrollHeight - el.clientHeight;
      if (scrollable <= 0) {
        this.hasReadEnough.set(true);
        return;
      }
      const scrollRatio = el.scrollTop / scrollable;
      if (scrollRatio >= 0.8) this.hasReadEnough.set(true);
    };
    window.addEventListener('scroll', check, { passive: true });
    this.scrollListener = () => window.removeEventListener('scroll', check);
    check();
  }

  ngOnDestroy(): void {
    if (this.scrollListener) this.scrollListener();
  }

  accept(): void {
    const doc = this.document();
    if (!doc) return;
    const version = doc.meta.version || 'v1.0';
    this.authService.acceptTerms(version).subscribe({
      next: () => {
        this.router.navigateByUrl(this.nextUrl());
      },
      error: () => {
        // stay on page, user can retry
      },
    });
  }
}
