import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../models/api.model';
import { User } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  /** Managers and admins: claims officers available for (re)assignment. */
  listOfficers(): Observable<User[]> {
    return this.http.get<User[]>(`${API_BASE_URL}/users/officers`);
  }
}
