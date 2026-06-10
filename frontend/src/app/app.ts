import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastContainerComponent } from './components/toast-container/toast-container';

/**
 * Root component of the Athenyx Ward SPA.
 *
 * Hosts the {@link RouterOutlet} (so all routed pages render inside
 * this host) and the global {@link ToastContainerComponent}.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainerComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
