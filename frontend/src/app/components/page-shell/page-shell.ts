import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';

@Component({
  selector: 'app-page-shell',
  standalone: true,
  imports: [],
  templateUrl: './page-shell.html',
  styleUrl: './page-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageShellComponent {
  readonly icon = input.required<string>();
  readonly title = input.required<string>({ alias: 'pageTitle' });
  readonly subtitle = input<string>('');
  readonly demo = input<boolean>(true);
}
