import { Claim, isTerminal } from '../models/claim.model';
import { User } from '../models/user.model';

export type ClaimAction =
  | 'assign'
  | 'reassign'
  | 'assess'
  | 'requestInfo'
  | 'approve'
  | 'reject'
  | 'settle'
  | 'priority'
  | 'edit'
  | 'provideInfo'
  | 'withdraw';

export const CLAIM_ACTION_LABELS: Record<ClaimAction, string> = {
  assign: 'Assign',
  reassign: 'Reassign',
  assess: 'Record assessment',
  requestInfo: 'Request information',
  approve: 'Approve',
  reject: 'Reject',
  settle: 'Settle',
  priority: 'Change priority',
  edit: 'Edit details',
  provideInfo: 'Provide information',
  withdraw: 'Withdraw',
};

/**
 * Mirrors the backend workflow and authorisation rules to decide which actions to offer in the UI.
 * The backend enforces the same rules; this only avoids showing actions that would be rejected.
 */
export function availableClaimActions(claim: Claim, user: User | null): ClaimAction[] {
  if (!user) {
    return [];
  }
  const actions: ClaimAction[] = [];
  const open = !isTerminal(claim.status);
  const inReview = claim.status === 'UNDER_REVIEW' || claim.status === 'INFO_REQUESTED';
  const unassignedNew = claim.status === 'SUBMITTED' && !claim.assignedOfficerId;

  switch (user.role) {
    case 'CLAIMANT':
      if (claim.claimantId !== user.id) {
        break;
      }
      if (claim.status === 'INFO_REQUESTED') {
        actions.push('provideInfo');
      }
      if (claim.status === 'SUBMITTED' || claim.status === 'INFO_REQUESTED') {
        actions.push('edit');
      }
      if (open && claim.status !== 'APPROVED') {
        actions.push('withdraw');
      }
      break;
    case 'CLAIMS_OFFICER': {
      const mine = claim.assignedOfficerId === user.id;
      if (unassignedNew) {
        actions.push('assign');
      }
      if (mine && inReview) {
        actions.push('assess');
      }
      if (mine && claim.status === 'UNDER_REVIEW') {
        actions.push('requestInfo', 'approve', 'reject');
      }
      if (mine && claim.status === 'APPROVED') {
        actions.push('settle');
      }
      if (open) {
        actions.push('priority');
      }
      break;
    }
    case 'MANAGER':
      if (unassignedNew) {
        actions.push('assign');
      }
      if (inReview) {
        actions.push('reassign');
      }
      if (claim.status === 'APPROVED') {
        actions.push('settle');
      }
      if (open) {
        actions.push('priority');
      }
      break;
    case 'ADMIN':
      if (unassignedNew) {
        actions.push('assign');
      }
      if (inReview) {
        actions.push('reassign');
      }
      break;
  }
  return actions;
}

/** Who may attach documents: the owning claimant and claims staff, while the claim is open. */
export function canUploadDocuments(claim: Claim, user: User | null): boolean {
  if (!user || isTerminal(claim.status)) {
    return false;
  }
  return user.role === 'CLAIMANT' ? claim.claimantId === user.id : user.role !== 'ADMIN';
}
