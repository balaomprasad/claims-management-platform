export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'SETTLED' | 'WITHDRAWN';

export const CLAIM_STATUSES: readonly ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'APPROVED',
  'REJECTED',
  'SETTLED',
  'WITHDRAWN',
];

export interface Claim {
  id: string;
  claimNumber: string;
  policyId: string;
  policyNumber: string;
  claimantId: string;
  claimantName: string;
  assignedAdjusterId: string | null;
  assignedAdjusterName: string | null;
  incidentDate: string;
  description: string;
  claimedAmount: number;
  approvedAmount: number | null;
  rejectionReason: string | null;
  status: ClaimStatus;
  createdAt: string;
  updatedAt: string;
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

export interface ClaimSummary {
  countsByStatus: Record<ClaimStatus, number>;
  total: number;
}

export interface CreateClaimRequest {
  policyId: string;
  incidentDate: string;
  description: string;
  claimedAmount: number;
}

export interface ApproveClaimRequest {
  approvedAmount: number;
  comment?: string;
}

export interface RejectClaimRequest {
  reason: string;
}

export interface AssignClaimRequest {
  adjusterId?: string;
  comment?: string;
}
