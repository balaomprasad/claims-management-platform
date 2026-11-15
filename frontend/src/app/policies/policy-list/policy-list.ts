import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import { Policy } from '../../core/models/policy.model';
import { humanize } from '../../core/models/reference.model';
import { AuthService } from '../../core/services/auth.service';
import { PolicyService } from '../../core/services/policy.service';
import { PageState } from '../../shared/page-state/page-state';

@Component({
  selector: 'app-policy-list',
  imports: [DatePipe, CurrencyPipe, MatTableModule, PageState],
  template: `
    <div class="page-header"><h1>Policies</h1></div>
    <app-page-state
      [loading]="loading()"
      [error]="error()"
      [empty]="policies().length === 0"
      emptyMessage="No policies found."
      (retry)="load()"
    >
      <div class="table-wrapper">
        <table mat-table [dataSource]="policies()" class="full-width">
          <ng-container matColumnDef="policyNumber">
            <th mat-header-cell *matHeaderCellDef>Policy #</th>
            <td mat-cell *matCellDef="let p">{{ p.policyNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="holderName">
            <th mat-header-cell *matHeaderCellDef>Holder</th>
            <td mat-cell *matCellDef="let p">{{ p.holderName }}</td>
          </ng-container>
          <ng-container matColumnDef="productType">
            <th mat-header-cell *matHeaderCellDef>Product</th>
            <td mat-cell *matCellDef="let p">{{ humanize(p.productType) }}</td>
          </ng-container>
          <ng-container matColumnDef="market">
            <th mat-header-cell *matHeaderCellDef>Market</th>
            <td mat-cell *matCellDef="let p">{{ humanize(p.market) }}</td>
          </ng-container>
          <ng-container matColumnDef="coverageAmount">
            <th mat-header-cell *matHeaderCellDef>Coverage</th>
            <td mat-cell *matCellDef="let p">{{ p.coverageAmount | currency: p.currency : 'code' }}</td>
          </ng-container>
          <ng-container matColumnDef="deductible">
            <th mat-header-cell *matHeaderCellDef>Deductible</th>
            <td mat-cell *matCellDef="let p">{{ p.deductible | currency: p.currency : 'code' }}</td>
          </ng-container>
          <ng-container matColumnDef="period">
            <th mat-header-cell *matHeaderCellDef>Period</th>
            <td mat-cell *matCellDef="let p">
              {{ p.startDate | date: 'mediumDate' }} – {{ p.endDate | date: 'mediumDate' }}
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let p">{{ humanize(p.status) }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
    </app-page-state>
  `,
})
export class PolicyList implements OnInit {
  private readonly policyService = inject(PolicyService);
  private readonly isClaimant = inject(AuthService).hasRole('CLAIMANT');

  protected readonly humanize = humanize;
  protected readonly policies = signal<Policy[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly columns = [
    'policyNumber',
    ...(this.isClaimant ? [] : ['holderName']),
    'productType',
    'market',
    'coverageAmount',
    'deductible',
    'period',
    'status',
  ];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.policyService.list(0, 100).subscribe({
      next: (page) => {
        this.policies.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
