import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { catchError, concatMap, from, map, of, switchMap, toArray } from 'rxjs';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import {
  ACCEPTED_DOCUMENT_TYPES,
  DOCUMENT_TYPES,
  DocumentType,
  MAX_DOCUMENT_BYTES,
} from '../../core/models/document.model';
import { Policy } from '../../core/models/policy.model';
import { humanize } from '../../core/models/reference.model';
import { ClaimService } from '../../core/services/claim.service';
import { DocumentService } from '../../core/services/document.service';
import { PolicyService } from '../../core/services/policy.service';
import { PageState } from '../../shared/page-state/page-state';

interface PendingDocument {
  file: File;
  type: DocumentType;
}

@Component({
  selector: 'app-report-incident',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageState,
  ],
  templateUrl: './report-incident.html',
})
export class ReportIncident implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly documentService = inject(DocumentService);
  private readonly policyService = inject(PolicyService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  /** One key per form instance: double-clicks and retries cannot create duplicate claims. */
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly humanize = humanize;
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly accept = ACCEPTED_DOCUMENT_TYPES;
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly policies = signal<Policy[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly documents = signal<PendingDocument[]>([]);
  protected readonly fileError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    policyId: ['', Validators.required],
    incidentDate: ['', Validators.required],
    incidentLocation: ['', [Validators.required, Validators.maxLength(300)]],
    incidentDescription: ['', [Validators.required, Validators.maxLength(2000)]],
    estimatedLoss: [0, [Validators.required, Validators.min(0.01)]],
  });

  private readonly selectedPolicyId = toSignal(this.form.controls.policyId.valueChanges, { initialValue: '' });
  protected readonly selectedPolicy = computed(() => this.policies().find((p) => p.id === this.selectedPolicyId()));

  ngOnInit(): void {
    this.loadPolicies();
  }

  protected loadPolicies(): void {
    this.loading.set(true);
    this.error.set(null);
    this.policyService.list().subscribe({
      next: (page) => {
        this.policies.set(page.content.filter((p) => p.status === 'ACTIVE'));
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected addFiles(input: HTMLInputElement): void {
    this.fileError.set(null);
    const files = Array.from(input.files ?? []);
    const tooLarge = files.filter((f) => f.size > MAX_DOCUMENT_BYTES);
    if (tooLarge.length) {
      this.fileError.set(`${tooLarge.map((f) => f.name).join(', ')} exceed the 10 MB limit.`);
    }
    const accepted = files.filter((f) => f.size <= MAX_DOCUMENT_BYTES && f.size > 0);
    this.documents.update((docs) => [...docs, ...accepted.map((file) => ({ file, type: 'PHOTO' as DocumentType }))]);
    input.value = '';
  }

  protected setType(index: number, type: DocumentType): void {
    this.documents.update((docs) => docs.map((d, i) => (i === index ? { ...d, type } : d)));
  }

  protected removeFile(index: number): void {
    this.documents.update((docs) => docs.filter((_, i) => i !== index));
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const pending = this.documents();
    this.claimService
      .submit(this.form.getRawValue(), this.idempotencyKey)
      .pipe(
        switchMap((claim) =>
          from(pending).pipe(
            // A failed upload must not hide the fact that the claim itself was created.
            concatMap((doc) =>
              this.documentService.upload(claim.id, doc.file, doc.type).pipe(
                map(() => true),
                catchError(() => of(false)),
              ),
            ),
            toArray(),
            map((results) => ({ claim, failedUploads: results.filter((ok) => !ok).length })),
          ),
        ),
      )
      .subscribe({
        next: ({ claim, failedUploads }) => {
          const message = failedUploads
            ? `Claim ${claim.claimNumber} submitted, but ${failedUploads} document(s) failed. Add them from the claim page.`
            : `Claim ${claim.claimNumber} submitted`;
          this.snackBar.open(message, 'OK', { duration: 6000 });
          void this.router.navigate(['/claims', claim.id]);
        },
        error: () => this.submitting.set(false),
      });
  }
}
