import { CurrencyPipe, DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { CLAIM_STATUSES } from '../../core/models/claim.model';
import { ManagerDashboard as Dashboard, DashboardFilter } from '../../core/models/dashboard.model';
import { CURRENCIES, MARKETS, humanize } from '../../core/models/reference.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { PageState } from '../../shared/page-state/page-state';
import { StatusChip } from '../../shared/status-chip/status-chip';

@Component({
  selector: 'app-manager-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    KeyValuePipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatExpansionModule,
    PageState,
    StatusChip,
  ],
  templateUrl: './manager-dashboard.html',
  styleUrl: './manager-dashboard.scss',
})
export class ManagerDashboard implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  protected readonly markets = MARKETS;
  protected readonly currencies = CURRENCIES;
  protected readonly statuses = CLAIM_STATUSES;
  protected readonly humanize = humanize;

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly filters = inject(NonNullableFormBuilder).group({
    market: [''],
    currency: [''],
    submittedFrom: [''],
    submittedTo: [''],
    ageingDays: [30],
  });

  /** Largest bar value, so status and ageing bars are drawn relative to it. */
  protected readonly maxStatusCount = computed(() =>
    Math.max(1, ...Object.values(this.data()?.claimsByStatus ?? {})),
  );
  protected readonly maxBucket = computed(() => Math.max(1, ...(this.data()?.ageing.buckets ?? []).map((b) => b.claims)));

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.load());
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const value = this.filters.getRawValue();
    const filter: DashboardFilter = {
      market: (value.market || null) as DashboardFilter['market'],
      currency: (value.currency || null) as DashboardFilter['currency'],
      submittedFrom: value.submittedFrom || null,
      submittedTo: value.submittedTo || null,
      ageingDays: value.ageingDays || null,
    };
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.manager(filter).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected resetFilters(): void {
    this.filters.reset();
  }
}
