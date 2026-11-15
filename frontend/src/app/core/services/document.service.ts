import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../models/api.model';
import { ClaimDocument, DocumentType } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);

  list(claimId: string): Observable<ClaimDocument[]> {
    return this.http.get<ClaimDocument[]>(this.url(claimId));
  }

  upload(claimId: string, file: File, documentType: DocumentType): Observable<ClaimDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    return this.http.post<ClaimDocument>(this.url(claimId), form);
  }

  /** Downloads through HttpClient so the bearer token is attached (a plain link would not authenticate). */
  download(claimId: string, documentId: string): Observable<Blob> {
    return this.http.get(`${this.url(claimId)}/${documentId}/content`, { responseType: 'blob' });
  }

  private url(claimId: string): string {
    return `${API_BASE_URL}/claims/${claimId}/documents`;
  }
}
