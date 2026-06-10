import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { AvatarService } from '../../services/avatar.service';

export type SenderAvatarSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-sender-avatar',
  standalone: true,
  imports: [],
  templateUrl: './sender-avatar.html',
  styleUrl: './sender-avatar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SenderAvatarComponent {
  private readonly avatars = inject(AvatarService);

  readonly sender = input<string>('');
  readonly senderName = input<string | null | undefined>(null);
  readonly size = input<SenderAvatarSize>('md');

  readonly state = computed(() => this.avatars.getState(this.sender(), this.senderName()));

  readonly imageUrl = computed(() => {
    const s = this.state();
    if (s.kind === 'gravatar') return s.gravatarUrl;
    if (s.kind === 'favicon') return s.faviconUrl;
    return '';
  });

  readonly showImage = computed(
    () => this.state().kind !== 'initials' && this.imageUrl() !== '',
  );

  readonly imageRef = viewChild<ElementRef<HTMLImageElement>>('avatarImg');
  readonly imageLoaded = signal(false);

  constructor() {
    effect(() => {
      this.imageUrl();
      this.imageLoaded.set(false);
      setTimeout(() => {
        const img = untracked(() => this.imageRef()?.nativeElement);
        if (img && img.complete && img.naturalWidth > 0) {
          this.imageLoaded.set(true);
        }
      }, 0);
    });
  }

  onLoad(): void {
    this.imageLoaded.set(true);
  }

  onError(): void {
    this.imageLoaded.set(false);
    const kind = this.state().kind;
    if (kind === 'gravatar' || kind === 'favicon') {
      this.avatars.markFailed(this.sender(), kind);
    }
  }
}
