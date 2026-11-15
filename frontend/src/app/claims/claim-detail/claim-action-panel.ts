import { CurrencyPipe } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CLAIM_PRIORITIES, Claim, ClaimPriority } from '../../core/models/claim.model';
import { humanize } from '../../core/models/reference.model';
import { User } from '../../core/models/user.model';
import { ClaimCommand } from '../../core/services/claim-workflow.service';
import { CLAIM_ACTION_LABELS, ClaimAction } from '../../core/utils/claim-actions';

/**
 * Renders the actions available to the current user and collects their input. It emits a typed
 * {@link ClaimCommand}; execution, errors and refresh are handled by the parent.
 */
@Component({
  selector: 'app-claim-action-panel',
  imports: [
    FormsModule,
    CurrencyPipe,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './claim-action-panel.html',
  styles: `
    .action-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding-top: 8px;
    }
    .danger {
      color: var(--mat-sys-error);
    }
    .action-title {
      margin: 0 0 8px;
      font: var(--mat-sys-title-small);
    }
  `,
})
export class ClaimActionPanel {
  readonly claim = input.required<Claim>();
  readonly actions = input.required<ClaimAction[]>();
  readonly officers = input<User[]>([]);
  readonly needsOfficerChoice = input(false);
  readonly busy = input(false);
  readonly command = output<ClaimCommand>();
  readonly actionStarted = output<ClaimAction>();

  protected readonly labels = CLAIM_ACTION_LABELS;
  protected readonly priorities = CLAIM_PRIORITIES;
  protected readonly humanize = humanize;
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly active = signal<ClaimAction | null>(null);

  // Form model for the active action.
  protected text = '';
  protected comment = '';
  protected amount: number | null = null;
  protected officerId = '';
  protected date = '';
  protected reference = '';
  protected location = '';
  protected priority: ClaimPriority = 'NORMAL';

  start(action: ClaimAction): void {
    const claim = this.claim();
    this.active.set(action);
    this.text = action === 'edit' ? claim.incidentDescription : '';
    this.comment = '';
    this.officerId = '';
    this.date = this.today;
    this.reference = '';
    this.location = claim.incidentLocation ?? '';
    this.priority = claim.priority;
    this.amount =
      action === 'approve' || action === 'edit'
        ? claim.estimatedLoss
        : action === 'settle'
          ? claim.approvedAmount
          : null;
    this.actionStarted.emit(action);
  }

  protected cancel(): void {
    this.active.set(null);
  }

  /** Clears the form once the parent has executed the command successfully. */
  reset(): void {
    this.active.set(null);
  }

  protected isValid(): boolean {
    switch (this.active()) {
      case 'assign':
        return !this.needsOfficerChoice() || this.officerId !== '';
      case 'reassign':
        return this.officerId !== '';
      case 'assess':
      case 'requestInfo':
      case 'reject':
      case 'provideInfo':
        return this.text.trim().length > 0;
      case 'approve':
        return this.amount !== null && this.amount > 0 && this.amount <= this.claim().estimatedLoss;
      case 'settle':
        return this.amount !== null && this.amount > 0 && this.amount <= (this.claim().approvedAmount ?? 0);
      case 'edit':
        return this.location.trim() !== '' && this.text.trim() !== '' && (this.amount ?? 0) > 0;
      default:
        return true;
    }
  }

  protected confirm(): void {
    const action = this.active();
    if (!action || !this.isValid()) {
      return;
    }
    const comment = this.comment.trim() || undefined;
    const text = this.text.trim();
    const commands: Record<ClaimAction, () => ClaimCommand> = {
      assign: () => ({ type: 'assign', officerId: this.officerId || undefined, comment }),
      reassign: () => ({ type: 'reassign', officerId: this.officerId, comment }),
      assess: () => ({ type: 'assess', notes: text, recommendedAmount: this.amount }),
      requestInfo: () => ({ type: 'requestInfo', message: text }),
      approve: () => ({ type: 'approve', approvedAmount: this.amount!, comment }),
      reject: () => ({ type: 'reject', reason: text }),
      settle: () => ({
        type: 'settle',
        settledAmount: this.amount!,
        settlementDate: this.date || undefined,
        settlementReference: this.reference.trim() || undefined,
      }),
      priority: () => ({ type: 'priority', priority: this.priority }),
      edit: () => ({
        type: 'edit',
        incidentLocation: this.location.trim(),
        incidentDescription: text,
        estimatedLoss: this.amount!,
      }),
      provideInfo: () => ({ type: 'provideInfo', response: text }),
      withdraw: () => ({ type: 'withdraw', comment }),
    };
    this.command.emit(commands[action]());
  }
}
