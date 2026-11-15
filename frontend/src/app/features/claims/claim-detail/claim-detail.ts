import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Claim, ClaimHistoryEntry } from '../../../core/models/claim.model';
import { User } from '../../../core/models/user.model';
import { AuthService } from '../../../core/services/auth.service';
import { ClaimService } from '../../../core/services/claim.service';
import { UserService } from '../../../core/services/user.service';
import { ClaimAction, availableClaimActions } from '../../../core/utils/claim-actions';
import { StatusChip } from '../../../shared/status-chip/status-chip';

const ACTION_LABELS: Record<ClaimAction, string> = {
  assign: 'Assign',
  approve: 'Approve',
  reject: 'Reject',
  settle: 'Settle',
  withdraw: 'Withdraw',
};

@Component({
  selector: 'app-claim-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    StatusChip,
  ],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetail implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly userService = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly labels = ACTION_LABELS;
  protected readonly claim = signal<Claim | null>(null);
  protected readonly history = signal<ClaimHistoryEntry[]>([]);
  protected readonly adjusters = signal<User[]>([]);
  protected readonly activeAction = signal<ClaimAction | null>(null);
  protected readonly busy = signal(false);
  protected readonly isAdmin = this.auth.hasRole('ADMIN');

  protected readonly actions = computed(() => {
    const claim = this.claim();
    return claim ? availableClaimActions(claim, this.auth.currentUser()) : [];
  });

  // Action form model (template-driven to keep the panel compact).
  protected approvedAmount: number | null = null;
  protected comment = '';
  protected reason = '';
  protected adjusterId = '';

  ngOnInit(): void {
    this.reload();
  }

  protected startAction(action: ClaimAction): void {
    this.activeAction.set(action);
    this.comment = '';
    this.reason = '';
    this.adjusterId = '';
    this.approvedAmount = this.claim()?.claimedAmount ?? null;
    if (action === 'assign' && this.isAdmin && this.adjusters().length === 0) {
      this.userService.listAdjusters().subscribe((page) => this.adjusters.set(page.content));
    }
  }

  protected cancelAction(): void {
    this.activeAction.set(null);
  }

  protected canConfirm(): boolean {
    switch (this.activeAction()) {
      case 'approve':
        return this.approvedAmount !== null && this.approvedAmount > 0;
      case 'reject':
        return this.reason.trim().length > 0;
      case 'assign':
        return !this.isAdmin || this.adjusterId !== '';
      default:
        return true;
    }
  }

  protected confirmAction(): void {
    const action = this.activeAction();
    if (!action || !this.canConfirm()) {
      return;
    }
    this.busy.set(true);
    this.execute(action).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.activeAction.set(null);
        this.busy.set(false);
        this.snackBar.open(`Claim ${claim.claimNumber} is now ${claim.status.replace('_', ' ')}`, 'OK', {
          duration: 3000,
        });
        this.loadHistory();
      },
      error: () => this.busy.set(false),
    });
  }

  private execute(action: ClaimAction): Observable<Claim> {
    const id = this.id();
    const comment = this.comment.trim() || undefined;
    switch (action) {
      case 'assign':
        return this.claimService.assign(id, { adjusterId: this.isAdmin ? this.adjusterId : undefined, comment });
      case 'approve':
        return this.claimService.approve(id, { approvedAmount: this.approvedAmount!, comment });
      case 'reject':
        return this.claimService.reject(id, { reason: this.reason.trim() });
      case 'settle':
        return this.claimService.settle(id, comment);
      case 'withdraw':
        return this.claimService.withdraw(id, comment);
    }
  }

  private reload(): void {
    this.claimService.get(this.id()).subscribe((claim) => this.claim.set(claim));
    this.loadHistory();
  }

  private loadHistory(): void {
    this.claimService.history(this.id()).subscribe((entries) => this.history.set(entries));
  }
}
