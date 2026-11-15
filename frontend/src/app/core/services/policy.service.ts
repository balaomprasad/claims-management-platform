import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../models/api.model';
import { Policy } from '../models/policy.model';

@Injectable({ providedIn: 'root' })
export class PolicyService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 50): Observable<Page<Policy>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Policy>>(`${API_BASE_URL}/policies`, { params });
  }
}
