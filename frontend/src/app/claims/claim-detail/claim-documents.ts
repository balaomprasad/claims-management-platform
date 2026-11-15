import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import {
  ACCEPTED_DOCUMENT_TYPES,
  ClaimDocument,
  DOCUMENT_TYPES,
  DocumentType,
  MAX_DOCUMENT_BYTES,
} from '../../core/models/document.model';
import { humanize } from '../../core/models/reference.model';
import { DocumentService } from '../../core/services/document.service';
import { PageState } from '../../shared/page-state/page-state';

/** Lists a claim's documents, downloads them with the user's token and uploads new ones when permitted. */
@Component({
  selector: 'app-claim-documents',
  imports: [DatePipe, DecimalPipe, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, PageState],
  template: `
    <mat-card>
      <mat-card-header><mat-card-title>Documents</mat-card-title></mat-card-header>
      <mat-card-content>
        <app-page-state
          [loading]="loading()"
          [error]="error()"
          [empty]="documents().length === 0"
          emptyMessage="No documents yet."
          (retry)="load()"
        >
          <ul class="doc-list">
            @for (doc of documents(); track doc.id) {
              <li>
                <mat-icon>{{ doc.contentType === 'application/pdf' ? 'picture_as_pdf' : 'image' }}</mat-icon>
                <div class="meta">
                  <button mat-button type="button" class="link" (click)="download(doc)">{{ doc.fileName }}</button>
                  <span class="muted small">
                    {{ humanize(doc.documentType) }} · {{ doc.sizeBytes / 1024 | number: '1.0-1' }} KB ·
                    {{ doc.uploadedByName }} · {{ doc.createdAt | date: 'short' }}
                  </span>
                </div>
              </li>
            }
          </ul>
        </app-page-state>
        @if (canUpload()) {
          <div class="upload">
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Document type</mat-label>
              <mat-select [value]="type()" (selectionChange)="type.set($event.value)">
                @for (t of documentTypes; track t) {
                  <mat-option [value]="t">{{ humanize(t) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <input #file type="file" hidden [accept]="accept" (change)="upload(file)" />
            <button mat-stroked-button type="button" [disabled]="uploading()" (click)="file.click()">
              <mat-icon>upload</mat-icon> {{ uploading() ? 'Uploading…' : 'Upload' }}
            </button>
          </div>
          <p class="muted small">PDF, PNG or JPEG, up to 10 MB.</p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .doc-list { list-style: none; padding: 0; margin: 0; }
    .doc-list li { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
    .meta { display: flex; flex-direction: column; min-width: 0; }
    .link { padding: 0; justify-content: flex-start; text-align: left; }
    .small { font-size: 12px; }
    .upload { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 12px; }
  `,
})
export class ClaimDocuments implements OnInit {
  private readonly documentService = inject(DocumentService);
  private readonly snackBar = inject(MatSnackBar);

  readonly claimId = input.required<string>();
  readonly canUpload = input(false);

  protected readonly humanize = humanize;
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly accept = ACCEPTED_DOCUMENT_TYPES;
  protected readonly documents = signal<ClaimDocument[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly type = signal<DocumentType>('PHOTO');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.documentService.list(this.claimId()).subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      this.snackBar.open(`${file.name} exceeds the 10 MB limit`, 'OK', { duration: 4000 });
      return;
    }
    this.uploading.set(true);
    this.documentService.upload(this.claimId(), file, this.type()).subscribe({
      next: (doc) => {
        this.documents.update((docs) => [...docs, doc]);
        this.uploading.set(false);
        this.snackBar.open(`${doc.fileName} uploaded`, 'OK', { duration: 3000 });
      },
      error: () => this.uploading.set(false),
    });
  }

  protected download(doc: ClaimDocument): void {
    this.documentService.download(this.claimId(), doc.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = doc.fileName;
      link.click();
      URL.revokeObjectURL(url);
    });
  }
}
