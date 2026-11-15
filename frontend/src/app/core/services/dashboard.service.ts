import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../models/api.model';
import { DashboardFilter, ManagerDashboard } from '../models/dashboard.model';
import { toParams } from '../utils/http-params';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  manager(filter: DashboardFilter): Observable<ManagerDashboard> {
    return this.http.get<ManagerDashboard>(`${API_BASE_URL}/dashboard/manager`, { params: toParams({ ...filter }) });
  }
}
