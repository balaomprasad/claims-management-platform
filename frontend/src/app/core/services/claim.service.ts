import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../models/api.model';
import {
  ApproveClaimRequest,
  AssignClaimRequest,
  Claim,
  ClaimHistoryEntry,
  ClaimStatus,
  ClaimSummary,
  CreateClaimRequest,
  RejectClaimRequest,
} from '../models/claim.model';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/claims`;

  list(page: number, size: number, status?: ClaimStatus | null): Observable<Page<Claim>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'createdAt,desc');
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<Claim>>(this.baseUrl, { params });
  }

  get(id: string): Observable<Claim> {
    return this.http.get<Claim>(`${this.baseUrl}/${id}`);
  }

  history(id: string): Observable<ClaimHistoryEntry[]> {
    return this.http.get<ClaimHistoryEntry[]>(`${this.baseUrl}/${id}/history`);
  }

  summary(): Observable<ClaimSummary> {
    return this.http.get<ClaimSummary>(`${this.baseUrl}/summary`);
  }

  submit(request: CreateClaimRequest): Observable<Claim> {
    return this.http.post<Claim>(this.baseUrl, request);
  }

  assign(id: string, request: AssignClaimRequest = {}): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/assign`, request);
  }

  approve(id: string, request: ApproveClaimRequest): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/approve`, request);
  }

  reject(id: string, request: RejectClaimRequest): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/reject`, request);
  }

  settle(id: string, comment?: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/settle`, { comment });
  }

  withdraw(id: string, comment?: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/withdraw`, { comment });
  }
}
