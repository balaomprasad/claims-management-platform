import { Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { debounceTime } from 'rxjs';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { CLAIM_PRIORITIES, CLAIM_STATUSES, Claim, ClaimSearch } from '../../core/models/claim.model';
import { MARKETS, PRODUCT_TYPES, humanize } from '../../core/models/reference.model';
import { AuthService } from '../../core/services/auth.service';
import { ClaimService } from '../../core/services/claim.service';
import { ClaimColumn, ClaimTable } from '../../shared/claim-table/claim-table';
import { PageState } from '../../shared/page-state/page-state';

@Component({
  selector: 'app-claim-list',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatPaginatorModule,
    ClaimTable,
    PageState,
  ],
  templateUrl: './claim-list.html',
})
export class ClaimList implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);

  protected readonly isClaimant = inject(AuthService).hasRole('CLAIMANT');
  protected readonly statuses = CLAIM_STATUSES;
  protected readonly priorities = CLAIM_PRIORITIES;
  protected readonly markets = MARKETS;
  protected readonly types = PRODUCT_TYPES;
  protected readonly humanize = humanize;
  protected readonly columns: ClaimColumn[] = this.isClaimant
    ? ['claimNumber', 'policy', 'claimType', 'incidentDate', 'estimatedLoss', 'status', 'updatedAt']
    : ['claimNumber', 'claimant', 'claimType', 'market', 'estimatedLoss', 'priority', 'status', 'officer', 'age'];

  protected readonly claims = signal<Claim[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly filters = inject(NonNullableFormBuilder).group({
    status: [''],
    market: [''],
    claimType: [''],
    priority: [''],
    unassigned: [false],
    openOnly: [false],
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(200), takeUntilDestroyed()).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const f = this.filters.getRawValue();
    const search: ClaimSearch = {
      status: (f.status || null) as ClaimSearch['status'],
      market: (f.market || null) as ClaimSearch['market'],
      claimType: (f.claimType || null) as ClaimSearch['claimType'],
      priority: (f.priority || null) as ClaimSearch['priority'],
      unassigned: f.unassigned,
      openOnly: f.openOnly,
    };
    this.loading.set(true);
    this.error.set(null);
    this.claimService.search(search, this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.claims.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected open(claim: Claim): void {
    void this.router.navigate(['/claims', claim.id]);
  }
}
