import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Policy } from '../../../core/models/policy.model';
import { ClaimService } from '../../../core/services/claim.service';
import { PolicyService } from '../../../core/services/policy.service';

@Component({
  selector: 'app-claim-form',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './claim-form.html',
})
export class ClaimForm implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly policyService = inject(PolicyService);
  private readonly router = inject(Router);

  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly policies = signal<Policy[]>([]);
  protected readonly submitting = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    policyId: ['', Validators.required],
    incidentDate: ['', Validators.required],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    claimedAmount: [0, [Validators.required, Validators.min(0.01)]],
  });

  private readonly selectedPolicyId = toSignal(this.form.controls.policyId.valueChanges, { initialValue: '' });
  protected readonly selectedPolicy = computed(() =>
    this.policies().find((policy) => policy.id === this.selectedPolicyId()),
  );

  ngOnInit(): void {
    this.policyService.list().subscribe((page) =>
      this.policies.set(page.content.filter((policy) => policy.status === 'ACTIVE')),
    );
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.claimService.submit(this.form.getRawValue()).subscribe({
      next: (claim) => void this.router.navigate(['/claims', claim.id]),
      error: () => this.submitting.set(false),
    });
  }
}
