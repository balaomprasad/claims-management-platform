import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { Claim } from '../../core/models/claim.model';
import { AuthService } from '../../core/services/auth.service';
import { ClaimService } from '../../core/services/claim.service';
import { claimantStats } from '../../core/utils/claim-stats';
import { ClaimColumn, ClaimTable } from '../../shared/claim-table/claim-table';
import { PageState } from '../../shared/page-state/page-state';

@Component({
  selector: 'app-claimant-dashboard',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, ClaimTable, PageState],
  templateUrl: './claimant-dashboard.html',
})
export class ClaimantDashboard implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);

  protected readonly user = inject(AuthService).currentUser;
  protected readonly claims = signal<Claim[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly stats = computed(() => claimantStats(this.claims()));
  protected readonly awaitingInfo = computed(() => this.claims().filter((c) => c.status === 'INFO_REQUESTED'));
  protected readonly columns: ClaimColumn[] = [
    'claimNumber',
    'claimType',
    'incidentDate',
    'estimatedLoss',
    'status',
    'updatedAt',
  ];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.claimService.search({}, 0, 100).subscribe({
      next: (page) => {
        this.claims.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected open(claim: Claim): void {
    void this.router.navigate(['/claims', claim.id]);
  }
}
