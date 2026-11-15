import { Claim } from '../models/claim.model';
import { User } from '../models/user.model';

export type ClaimAction = 'assign' | 'approve' | 'reject' | 'settle' | 'withdraw';

/**
 * Mirrors the backend workflow rules to decide which actions to offer in the UI.
 * The backend enforces the same rules; this only avoids showing buttons that would fail.
 */
export function availableClaimActions(claim: Claim, user: User | null): ClaimAction[] {
  if (!user) {
    return [];
  }
  const actions: ClaimAction[] = [];
  switch (user.role) {
    case 'CLAIMANT':
      if (claim.claimantId === user.id && ['SUBMITTED', 'UNDER_REVIEW'].includes(claim.status)) {
        actions.push('withdraw');
      }
      break;
    case 'ADJUSTER':
      if (claim.status === 'SUBMITTED') {
        actions.push('assign');
      }
      if (claim.status === 'UNDER_REVIEW' && claim.assignedAdjusterId === user.id) {
        actions.push('approve', 'reject');
      }
      break;
    case 'ADMIN':
      if (claim.status === 'SUBMITTED') {
        actions.push('assign');
      }
      if (claim.status === 'APPROVED') {
        actions.push('settle');
      }
      break;
  }
  return actions;
}
