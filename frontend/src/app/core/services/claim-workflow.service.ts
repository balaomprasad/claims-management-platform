import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClaimPriority } from '../models/claim.model';
import { ClaimService } from './claim.service';

/** A user's intent on a claim, produced by the action panel and executed by {@link ClaimWorkflowService}. */
export type ClaimCommand =
  | { type: 'assign'; officerId?: string; comment?: string }
  | { type: 'reassign'; officerId: string; comment?: string }
  | { type: 'assess'; notes: string; recommendedAmount?: number | null }
  | { type: 'requestInfo'; message: string }
  | { type: 'approve'; approvedAmount: number; comment?: string }
  | { type: 'reject'; reason: string }
  | { type: 'settle'; settledAmount: number; settlementDate?: string; settlementReference?: string }
  | { type: 'priority'; priority: ClaimPriority }
  | { type: 'edit'; incidentLocation: string; incidentDescription: string; estimatedLoss: number }
  | { type: 'provideInfo'; response: string }
  | { type: 'withdraw'; comment?: string };

@Injectable({ providedIn: 'root' })
export class ClaimWorkflowService {
  private readonly claims = inject(ClaimService);

  execute(claimId: string, command: ClaimCommand): Observable<unknown> {
    switch (command.type) {
      case 'assign':
      case 'reassign':
        return this.claims.assign(claimId, command.officerId, command.comment);
      case 'assess':
        return this.claims.assess(claimId, command.notes, command.recommendedAmount);
      case 'requestInfo':
        return this.claims.requestInformation(claimId, command.message);
      case 'approve':
        return this.claims.approve(claimId, command.approvedAmount, command.comment);
      case 'reject':
        return this.claims.reject(claimId, command.reason);
      case 'settle':
        return this.claims.settle(claimId, command);
      case 'priority':
        return this.claims.changePriority(claimId, command.priority);
      case 'edit':
        return this.claims.update(claimId, command);
      case 'provideInfo':
        return this.claims.provideInformation(claimId, command.response);
      case 'withdraw':
        return this.claims.withdraw(claimId, command.comment);
    }
  }
}
