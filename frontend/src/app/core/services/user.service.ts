import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../models/api.model';
import { User } from '../models/user.model';

/** Admin-only user queries. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  listAdjusters(): Observable<Page<User>> {
    const params = new HttpParams().set('role', 'ADJUSTER').set('size', 100);
    return this.http.get<Page<User>>(`${API_BASE_URL}/admin/users`, { params });
  }
}
