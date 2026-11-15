import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../models/api.model';
import { AppNotification } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/notifications`;

  list(unreadOnly = false, page = 0, size = 50): Observable<Page<AppNotification>> {
    const params = new HttpParams().set('unreadOnly', unreadOnly).set('page', page).set('size', size);
    return this.http.get<Page<AppNotification>>(this.baseUrl, { params });
  }

  markRead(id: string): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/read`, null);
  }
}
