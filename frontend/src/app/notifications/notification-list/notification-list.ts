import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { AppNotification } from '../../core/models/notification.model';
import { NotificationService } from '../../core/services/notification.service';
import { PageState } from '../../shared/page-state/page-state';

@Component({
  selector: 'app-notification-list',
  imports: [DatePipe, RouterLink, MatListModule, MatIconModule, MatButtonModule, MatSlideToggleModule, PageState],
  template: `
    <div class="page-header">
      <h1>Notifications</h1>
      <mat-slide-toggle [checked]="unreadOnly()" (change)="toggleUnread($event.checked)">Unread only</mat-slide-toggle>
    </div>
    <app-page-state
      [loading]="loading()"
      [error]="error()"
      [empty]="notifications().length === 0"
      emptyMessage="No notifications."
      (retry)="load()"
    >
      <mat-list>
        @for (n of notifications(); track n.id) {
          <mat-list-item [class.unread]="!n.read">
            <mat-icon matListItemIcon>{{ n.read ? 'drafts' : 'mark_email_unread' }}</mat-icon>
            <a matListItemTitle [routerLink]="['/claims', n.claimId]">{{ n.message }}</a>
            <span matListItemLine class="muted">{{ n.createdAt | date: 'short' }}</span>
            @if (!n.read) {
              <button matListItemMeta mat-icon-button aria-label="Mark as read" (click)="markRead(n)">
                <mat-icon>done</mat-icon>
              </button>
            }
          </mat-list-item>
        }
      </mat-list>
    </app-page-state>
  `,
  styles: `
    .unread {
      font-weight: 600;
    }
  `,
})
export class NotificationList implements OnInit {
  private readonly notificationService = inject(NotificationService);

  protected readonly notifications = signal<AppNotification[]>([]);
  protected readonly unreadOnly = signal(false);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected toggleUnread(unreadOnly: boolean): void {
    this.unreadOnly.set(unreadOnly);
    this.load();
  }

  protected markRead(notification: AppNotification): void {
    this.notificationService.markRead(notification.id).subscribe(() =>
      this.notifications.update((list) =>
        this.unreadOnly()
          ? list.filter((n) => n.id !== notification.id)
          : list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)),
      ),
    );
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.notificationService.list(this.unreadOnly()).subscribe({
      next: (page) => {
        this.notifications.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
