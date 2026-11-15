import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../models/api.model';
import {
  Claim,
  ClaimAssessment,
  ClaimDecision,
  ClaimHistoryEntry,
  ClaimInformationRequest,
  ClaimPriority,
  ClaimSearch,
  ClaimSummary,
  CreateClaimRequest,
  SettleClaimRequest,
  UpdateClaimRequest,
} from '../models/claim.model';
import { toParams } from '../utils/http-params';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/claims`;

  search(search: ClaimSearch, page: number, size: number, sort = 'createdAt,desc'): Observable<Page<Claim>> {
    const params = toParams({ ...search, page, size, sort });
    return this.http.get<Page<Claim>>(this.baseUrl, { params });
  }

  get(id: string): Observable<Claim> {
    return this.http.get<Claim>(`${this.baseUrl}/${id}`);
  }

  getByNumber(claimNumber: string): Observable<Claim> {
    return this.http.get<Claim>(`${this.baseUrl}/by-number/${encodeURIComponent(claimNumber)}`);
  }

  summary(): Observable<ClaimSummary> {
    return this.http.get<ClaimSummary>(`${this.baseUrl}/summary`);
  }

  history(id: string): Observable<ClaimHistoryEntry[]> {
    return this.http.get<ClaimHistoryEntry[]>(`${this.baseUrl}/${id}/history`);
  }

  decision(id: string): Observable<ClaimDecision> {
    return this.http.get<ClaimDecision>(`${this.baseUrl}/${id}/decision`);
  }

  informationRequests(id: string): Observable<ClaimInformationRequest[]> {
    return this.http.get<ClaimInformationRequest[]>(`${this.baseUrl}/${id}/information-requests`);
  }

  assessments(id: string): Observable<ClaimAssessment[]> {
    return this.http.get<ClaimAssessment[]>(`${this.baseUrl}/${id}/assessments`);
  }

  /**
   * The idempotency key must be generated once per user intent (e.g. per form) so that retries and
   * double-clicks return the same claim instead of creating duplicates.
   */
  submit(request: CreateClaimRequest, idempotencyKey: string): Observable<Claim> {
    return this.http.post<Claim>(this.baseUrl, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  update(id: string, request: UpdateClaimRequest): Observable<Claim> {
    return this.http.put<Claim>(`${this.baseUrl}/${id}`, request);
  }

  provideInformation(id: string, response: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/information`, { response });
  }

  withdraw(id: string, comment?: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/withdraw`, { comment });
  }

  assign(id: string, officerId?: string, comment?: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/assign`, { officerId, comment });
  }

  assess(id: string, notes: string, recommendedAmount?: number | null): Observable<ClaimAssessment> {
    return this.http.post<ClaimAssessment>(`${this.baseUrl}/${id}/assessments`, { notes, recommendedAmount });
  }

  requestInformation(id: string, message: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/request-information`, { message });
  }

  approve(id: string, approvedAmount: number, comment?: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/approve`, { approvedAmount, comment });
  }

  reject(id: string, reason: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/reject`, { reason });
  }

  settle(id: string, request: SettleClaimRequest): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/settle`, request);
  }

  changePriority(id: string, priority: ClaimPriority): Observable<Claim> {
    return this.http.put<Claim>(`${this.baseUrl}/${id}/priority`, { priority });
  }
}
