import { CurrencyCode, Market, ProductType } from './reference.model';

export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'INFO_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED'
  | 'WITHDRAWN';

export type ClaimPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export const CLAIM_STATUSES: readonly ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'INFO_REQUESTED',
  'APPROVED',
  'REJECTED',
  'SETTLED',
  'WITHDRAWN',
];
export const CLAIM_PRIORITIES: readonly ClaimPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
export const TERMINAL_STATUSES: readonly ClaimStatus[] = ['REJECTED', 'SETTLED', 'WITHDRAWN'];

export interface Claim {
  id: string;
  claimNumber: string;
  policyId: string;
  policyNumber: string;
  claimType: ProductType;
  market: Market;
  currency: CurrencyCode;
  claimantId: string;
  claimantName: string;
  assignedOfficerId: string | null;
  assignedOfficerName: string | null;
  incidentDate: string;
  incidentLocation: string | null;
  incidentDescription: string;
  estimatedLoss: number;
  approvedAmount: number | null;
  settledAmount: number | null;
  settlementDate: string | null;
  settlementReference: string | null;
  priority: ClaimPriority;
  status: ClaimStatus;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
}

export interface ClaimSearch {
  status?: ClaimStatus | null;
  market?: Market | null;
  claimType?: ProductType | null;
  priority?: ClaimPriority | null;
  unassigned?: boolean;
  assignedToMe?: boolean;
  openOnly?: boolean;
  submittedFrom?: string | null;
  submittedTo?: string | null;
}

export interface ClaimHistoryEntry {
  id: string;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  changedById: string;
  changedByName: string;
  comment: string | null;
  changedAt: string;
}

export interface ClaimDecision {
  id: string;
  outcome: 'APPROVED' | 'REJECTED';
  amount: number | null;
  reason: string | null;
  decidedById: string;
  decidedByName: string;
  decidedAt: string;
}

export interface ClaimInformationRequest {
  id: string;
  message: string;
  requestedByName: string;
  requestedAt: string;
  response: string | null;
  respondedByName: string | null;
  respondedAt: string | null;
}

export interface ClaimAssessment {
  id: string;
  officerId: string;
  officerName: string;
  notes: string;
  recommendedAmount: number | null;
  createdAt: string;
}

export interface ClaimSummary {
  countsByStatus: Record<ClaimStatus, number>;
  total: number;
}

export interface CreateClaimRequest {
  policyId: string;
  incidentDate: string;
  incidentLocation: string;
  incidentDescription: string;
  estimatedLoss: number;
}

export type UpdateClaimRequest = Omit<CreateClaimRequest, 'policyId' | 'incidentDate'>;

export interface SettleClaimRequest {
  settledAmount: number;
  settlementDate?: string;
  settlementReference?: string;
}

export function isTerminal(status: ClaimStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

/** Whole days since submission (for open claims) or until closure (for closed claims). */
export function claimAgeDays(claim: Pick<Claim, 'createdAt' | 'closedAt'>, now = Date.now()): number {
  const end = claim.closedAt ? Date.parse(claim.closedAt) : now;
  return Math.max(0, Math.floor((end - Date.parse(claim.createdAt)) / 86_400_000));
}
