import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/**
 * Uniform loading, error and empty states. Content is projected only when the data is ready and non-empty.
 */
@Component({
  selector: 'app-page-state',
  imports: [MatProgressBarModule, MatButtonModule, MatIconModule],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    }
    @if (error(); as message) {
      <div class="state error" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ message }}</span>
        <button mat-stroked-button type="button" (click)="retry.emit()">Retry</button>
      </div>
    } @else if (!loading() && empty()) {
      <div class="state empty">
        <mat-icon>inbox</mat-icon>
        <span>{{ emptyMessage() }}</span>
      </div>
    } @else {
      <ng-content />
    }
  `,
  styles: `
    .state {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 24px 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class PageState {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyMessage = input('Nothing to show yet.');
  readonly retry = output<void>();
}
