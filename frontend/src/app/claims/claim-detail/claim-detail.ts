import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { forkJoin, of, switchMap } from 'rxjs';
import { toErrorMessage } from '../../core/interceptors/error.interceptor';
import {
  Claim,
  ClaimAssessment,
  ClaimDecision,
  ClaimHistoryEntry,
  ClaimInformationRequest,
  ClaimStatus,
  claimAgeDays,
} from '../../core/models/claim.model';
import { humanize } from '../../core/models/reference.model';
import { User } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ClaimCommand, ClaimWorkflowService } from '../../core/services/claim-workflow.service';
import { ClaimService } from '../../core/services/claim.service';
import { UserService } from '../../core/services/user.service';
import { ClaimAction, availableClaimActions, canUploadDocuments } from '../../core/utils/claim-actions';
import { PageState } from '../../shared/page-state/page-state';
import { PriorityChip } from '../../shared/priority-chip/priority-chip';
import { StatusChip } from '../../shared/status-chip/status-chip';
import { ClaimActionPanel } from './claim-action-panel';
import { ClaimDocuments } from './claim-documents';

const DECIDED_STATUSES: readonly ClaimStatus[] = ['APPROVED', 'REJECTED', 'SETTLED'];

@Component({
  selector: 'app-claim-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    PageState,
    StatusChip,
    PriorityChip,
    ClaimActionPanel,
    ClaimDocuments,
  ],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetail implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly workflow = inject(ClaimWorkflowService);
  private readonly userService = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly actionPanel = viewChild(ClaimActionPanel);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly humanize = humanize;
  protected readonly user = this.auth.currentUser;
  protected readonly isStaff = this.auth.hasRole('CLAIMS_OFFICER', 'MANAGER', 'ADMIN');
  protected readonly canChooseOfficer = this.auth.hasRole('MANAGER', 'ADMIN');

  protected readonly claim = signal<Claim | null>(null);
  protected readonly history = signal<ClaimHistoryEntry[]>([]);
  protected readonly decision = signal<ClaimDecision | null>(null);
  protected readonly infoRequests = signal<ClaimInformationRequest[]>([]);
  protected readonly assessments = signal<ClaimAssessment[]>([]);
  protected readonly officers = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly actions = computed<ClaimAction[]>(() => {
    const claim = this.claim();
    return claim ? availableClaimActions(claim, this.user()) : [];
  });
  protected readonly canUpload = computed(() => {
    const claim = this.claim();
    return claim ? canUploadDocuments(claim, this.user()) : false;
  });
  protected readonly ageDays = computed(() => {
    const claim = this.claim();
    return claim ? claimAgeDays(claim) : 0;
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    const id = this.id();
    this.claimService
      .get(id)
      .pipe(
        switchMap((claim) =>
          forkJoin({
            claim: of(claim),
            history: this.claimService.history(id),
            infoRequests: this.claimService.informationRequests(id),
            // Only decided claims have a decision; asking for others would be a (noisy) 404.
            decision: DECIDED_STATUSES.includes(claim.status) ? this.claimService.decision(id) : of(null),
            assessments: this.isStaff ? this.claimService.assessments(id) : of([]),
          }),
        ),
      )
      .subscribe({
      next: (data) => {
        this.claim.set(data.claim);
        this.history.set(data.history);
        this.infoRequests.set(data.infoRequests);
        this.decision.set(data.decision);
        this.assessments.set(data.assessments);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected onActionStarted(action: ClaimAction): void {
    if ((action === 'assign' || action === 'reassign') && this.canChooseOfficer && this.officers().length === 0) {
      this.userService.listOfficers().subscribe((officers) => this.officers.set(officers));
    }
  }

  protected execute(command: ClaimCommand): void {
    const claim = this.claim();
    if (!claim) {
      return;
    }
    this.busy.set(true);
    this.workflow.execute(claim.id, command).subscribe({
      next: () => {
        this.busy.set(false);
        this.actionPanel()?.reset();
        this.snackBar.open('Claim updated', 'OK', { duration: 3000 });
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }
}
