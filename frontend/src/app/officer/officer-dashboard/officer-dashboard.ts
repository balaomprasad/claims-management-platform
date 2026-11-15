import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { Claim, claimAgeDays } from '../../core/models/claim.model';
import { ClaimService } from '../../core/services/claim.service';
import { sortByUrgency } from '../../core/utils/claim-stats';
import { ClaimColumn, ClaimTable } from '../../shared/claim-table/claim-table';
import { PageState } from '../../shared/page-state/page-state';

const AGEING_THRESHOLD_DAYS = 30;

@Component({
  selector: 'app-officer-dashboard',
  imports: [MatCardModule, MatTabsModule, ClaimTable, PageState],
  templateUrl: './officer-dashboard.html',
})
export class OfficerDashboard implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly threshold = AGEING_THRESHOLD_DAYS;
  protected readonly mine = signal<Claim[]>([]);
  protected readonly queue = signal<Claim[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly awaitingInfo = computed(() => this.mine().filter((c) => c.status === 'INFO_REQUESTED').length);
  protected readonly readyToDecide = computed(() => this.mine().filter((c) => c.status === 'UNDER_REVIEW').length);
  protected readonly toSettle = computed(() => this.mine().filter((c) => c.status === 'APPROVED').length);
  protected readonly overdue = computed(
    () => [...this.mine(), ...this.queue()].filter((c) => claimAgeDays(c) > AGEING_THRESHOLD_DAYS).length,
  );

  protected readonly mineColumns: ClaimColumn[] = [
    'claimNumber', 'claimant', 'claimType', 'market', 'estimatedLoss', 'priority', 'status', 'age',
  ];
  protected readonly queueColumns: ClaimColumn[] = [
    'claimNumber', 'claimant', 'claimType', 'market', 'estimatedLoss', 'priority', 'age', 'assign',
  ];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      mine: this.claimService.search({ assignedToMe: true, openOnly: true }, 0, 100),
      queue: this.claimService.search({ unassigned: true, status: 'SUBMITTED' }, 0, 100),
    }).subscribe({
      next: ({ mine, queue }) => {
        this.mine.set(sortByUrgency(mine.content));
        this.queue.set(sortByUrgency(queue.content));
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

  protected assignToMe(claim: Claim): void {
    this.claimService.assign(claim.id).subscribe({
      next: () => {
        this.snackBar.open(`Claim ${claim.claimNumber} assigned to you`, 'Open', { duration: 4000 })
          .onAction()
          .subscribe(() => this.open(claim));
        this.load();
      },
      // Most likely another officer took it first (409); refresh the queue.
      error: () => this.load(),
    });
  }
}
