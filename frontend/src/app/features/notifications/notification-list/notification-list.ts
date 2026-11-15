import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { AppNotification } from '../../../core/models/notification.model';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-notification-list',
  imports: [DatePipe, RouterLink, MatListModule, MatIconModule, MatButtonModule, MatSlideToggleModule],
  template: `
    <div class="page-header">
      <h1>Notifications</h1>
      <mat-slide-toggle [checked]="unreadOnly()" (change)="toggleUnread($event.checked)">
        Unread only
      </mat-slide-toggle>
    </div>
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
      } @empty {
        <p class="muted">No notifications yet.</p>
      }
    </mat-list>
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

  ngOnInit(): void {
    this.load();
  }

  protected toggleUnread(unreadOnly: boolean): void {
    this.unreadOnly.set(unreadOnly);
    this.load();
  }

  protected markRead(notification: AppNotification): void {
    this.notificationService.markRead(notification.id).subscribe(() => this.load());
  }

  private load(): void {
    this.notificationService.list(this.unreadOnly()).subscribe((page) => this.notifications.set(page.content));
  }
}
