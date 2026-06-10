import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  PLATFORM_ID,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  selector: 'app-email-body',
  standalone: true,
  imports: [],
  templateUrl: './email-body.html',
  styleUrl: './email-body.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailBodyComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly onMessage = (event: MessageEvent) => this.handleMessage(event);

  readonly htmlContent = input<string | null>(null);
  readonly contentForAnalysis = input<string | null>(null);

  readonly safeHtml = signal<SafeHtml | null>(null);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      window.addEventListener('message', this.onMessage);
      this.destroyRef.onDestroy(() => {
        window.removeEventListener('message', this.onMessage);
      });
    }
    effect(() => {
      this.rebuild(this.htmlContent());
    });
  }

  private rebuild(html: string | null): void {
    if (!html) {
      this.safeHtml.set(null);
      return;
    }
    const script = `
      <script>
        function sendHeight() {
          window.parent.postMessage({ iframeHeight: document.body.scrollHeight }, '*');
        }
        window.addEventListener('load', sendHeight);
        new ResizeObserver(sendHeight).observe(document.body);
      <\/script>
    `;
    const finalHtml = html.replace('</body>', script + '</body>');
    this.safeHtml.set(this.sanitizer.bypassSecurityTrustHtml(finalHtml));
  }

  private handleMessage(event: MessageEvent): void {
    if (!event.data?.iframeHeight) return;
    const iframe = document.querySelector('app-email-body iframe') as HTMLIFrameElement | null;
    if (!iframe) return;
    iframe.style.height = event.data.iframeHeight + 'px';
  }
}
