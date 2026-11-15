import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { Claim, claimAgeDays } from '../../core/models/claim.model';
import { humanize } from '../../core/models/reference.model';
import { PriorityChip } from '../priority-chip/priority-chip';
import { StatusChip } from '../status-chip/status-chip';

export type ClaimColumn =
  | 'claimNumber'
  | 'claimant'
  | 'policy'
  | 'claimType'
  | 'market'
  | 'incidentDate'
  | 'estimatedLoss'
  | 'status'
  | 'priority'
  | 'officer'
  | 'age'
  | 'updatedAt'
  | 'assign';

/** Reusable, responsive claims table. Rows navigate via (open); the optional assign column emits (assign). */
@Component({
  selector: 'app-claim-table',
  imports: [MatTableModule, MatButtonModule, CurrencyPipe, DatePipe, StatusChip, PriorityChip],
  templateUrl: './claim-table.html',
  styleUrl: './claim-table.scss',
})
export class ClaimTable {
  readonly claims = input.required<Claim[]>();
  readonly columns = input.required<ClaimColumn[]>();
  readonly open = output<Claim>();
  readonly assign = output<Claim>();

  protected readonly humanize = humanize;
  protected readonly ageDays = (claim: Claim) => claimAgeDays(claim);

  protected onAssign(event: Event, claim: Claim): void {
    event.stopPropagation();
    this.assign.emit(claim);
  }
}
