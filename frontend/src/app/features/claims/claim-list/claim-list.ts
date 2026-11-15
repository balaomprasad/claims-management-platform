import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { CLAIM_STATUSES, Claim, ClaimStatus, ClaimSummary } from '../../../core/models/claim.model';
import { AuthService } from '../../../core/services/auth.service';
import { ClaimService } from '../../../core/services/claim.service';
import { StatusChip } from '../../../shared/status-chip/status-chip';

@Component({
  selector: 'app-claim-list',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    StatusChip,
  ],
  templateUrl: './claim-list.html',
  styleUrl: './claim-list.scss',
})
export class ClaimList implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly statuses = CLAIM_STATUSES;
  protected readonly isClaimant = this.auth.hasRole('CLAIMANT');
  protected readonly isStaff = this.auth.hasRole('ADJUSTER', 'ADMIN');
  protected readonly columns = this.isClaimant
    ? ['claimNumber', 'policyNumber', 'incidentDate', 'claimedAmount', 'status', 'createdAt']
    : ['claimNumber', 'claimantName', 'assignedAdjusterName', 'claimedAmount', 'status', 'createdAt'];

  protected readonly claims = signal<Claim[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly summary = signal<ClaimSummary | null>(null);
  protected readonly statusFilter = signal<ClaimStatus | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);

  ngOnInit(): void {
    this.load();
    if (this.isStaff) {
      this.claimService.summary().subscribe((summary) => this.summary.set(summary));
    }
  }

  protected onStatusChange(status: ClaimStatus | null): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
    this.load();
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected open(claim: Claim): void {
    void this.router.navigate(['/claims', claim.id]);
  }

  private load(): void {
    this.loading.set(true);
    this.claimService.list(this.pageIndex(), this.pageSize(), this.statusFilter()).subscribe({
      next: (page) => {
        this.claims.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
