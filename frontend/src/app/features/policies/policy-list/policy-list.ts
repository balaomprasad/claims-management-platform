import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { Policy } from '../../../core/models/policy.model';
import { AuthService } from '../../../core/services/auth.service';
import { PolicyService } from '../../../core/services/policy.service';

@Component({
  selector: 'app-policy-list',
  imports: [DatePipe, DecimalPipe, MatTableModule],
  template: `
    <div class="page-header"><h1>Policies</h1></div>
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
          <td mat-cell *matCellDef="let p">{{ p.productType }}</td>
        </ng-container>
        <ng-container matColumnDef="coverageAmount">
          <th mat-header-cell *matHeaderCellDef>Coverage</th>
          <td mat-cell *matCellDef="let p">{{ p.coverageAmount | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="deductible">
          <th mat-header-cell *matHeaderCellDef>Deductible</th>
          <td mat-cell *matCellDef="let p">{{ p.deductible | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="period">
          <th mat-header-cell *matHeaderCellDef>Period</th>
          <td mat-cell *matCellDef="let p">
            {{ p.startDate | date: 'mediumDate' }} – {{ p.endDate | date: 'mediumDate' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let p">{{ p.status }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="padding: 16px">No policies found.</td>
        </tr>
      </table>
    </div>
  `,
})
export class PolicyList implements OnInit {
  private readonly policyService = inject(PolicyService);
  private readonly isClaimant = inject(AuthService).hasRole('CLAIMANT');

  protected readonly policies = signal<Policy[]>([]);
  protected readonly columns = [
    'policyNumber',
    ...(this.isClaimant ? [] : ['holderName']),
    'productType',
    'coverageAmount',
    'deductible',
    'period',
    'status',
  ];

  ngOnInit(): void {
    this.policyService.list().subscribe((page) => this.policies.set(page.content));
  }
}
