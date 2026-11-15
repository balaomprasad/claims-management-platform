import { Claim } from '../models/claim.model';
import { User } from '../models/user.model';
import { availableClaimActions, canUploadDocuments } from './claim-actions';

const claimant: User = { id: 'c1', email: 'c@x.io', fullName: 'Casey', role: 'CLAIMANT' };
const officer: User = { id: 'o1', email: 'o@x.io', fullName: 'Jordan', role: 'CLAIMS_OFFICER' };
const otherOfficer: User = { id: 'o2', email: 'o2@x.io', fullName: 'Priya', role: 'CLAIMS_OFFICER' };
const manager: User = { id: 'm1', email: 'm@x.io', fullName: 'Morgan', role: 'MANAGER' };
const admin: User = { id: 'a1', email: 'a@x.io', fullName: 'Alex', role: 'ADMIN' };

function claim(overrides: Partial<Claim>): Claim {
  return {
    id: 'claim-1',
    claimNumber: 'CLM-2026-000001',
    policyId: 'p1',
    policyNumber: 'POL-1',
    claimType: 'MOTOR',
    market: 'SINGAPORE',
    currency: 'SGD',
    claimantId: claimant.id,
    claimantName: claimant.fullName,
    assignedOfficerId: null,
    assignedOfficerName: null,
    incidentDate: '2026-11-01',
    incidentLocation: 'Orchard Road',
    incidentDescription: 'Hail damage',
    estimatedLoss: 1000,
    approvedAmount: null,
    settledAmount: null,
    settlementDate: null,
    settlementReference: null,
    priority: 'NORMAL',
    status: 'SUBMITTED',
    createdAt: '2026-11-02T00:00:00Z',
    updatedAt: '2026-11-02T00:00:00Z',
    closedAt: null,
    ...overrides,
  };
}

describe('availableClaimActions', () => {
  it('lets the owning claimant edit, respond and withdraw only in the right states', () => {
    expect(availableClaimActions(claim({ status: 'SUBMITTED' }), claimant)).toEqual(['edit', 'withdraw']);
    expect(availableClaimActions(claim({ status: 'INFO_REQUESTED' }), claimant)).toEqual([
      'provideInfo',
      'edit',
      'withdraw',
    ]);
    expect(availableClaimActions(claim({ status: 'APPROVED' }), claimant)).toEqual([]);
    expect(availableClaimActions(claim({ claimantId: 'someone-else' }), claimant)).toEqual([]);
  });

  it('only offers decisions and settlement to the assigned officer', () => {
    const inReview = claim({ status: 'UNDER_REVIEW', assignedOfficerId: officer.id });
    expect(availableClaimActions(inReview, officer)).toEqual([
      'assess',
      'requestInfo',
      'approve',
      'reject',
      'priority',
    ]);
    expect(availableClaimActions(inReview, otherOfficer)).toEqual(['priority']);
    expect(availableClaimActions(claim({ status: 'APPROVED', assignedOfficerId: officer.id }), officer)).toEqual([
      'settle',
      'priority',
    ]);
  });

  it('offers self-assignment of unassigned submitted claims to officers', () => {
    expect(availableClaimActions(claim({}), officer)).toEqual(['assign', 'priority']);
  });

  it('lets managers assign, reassign and settle but never decide', () => {
    expect(availableClaimActions(claim({}), manager)).toEqual(['assign', 'priority']);
    expect(availableClaimActions(claim({ status: 'UNDER_REVIEW', assignedOfficerId: officer.id }), manager)).toEqual([
      'reassign',
      'priority',
    ]);
    expect(availableClaimActions(claim({ status: 'APPROVED', assignedOfficerId: officer.id }), manager)).toEqual([
      'settle',
      'priority',
    ]);
    expect(availableClaimActions(claim({ status: 'SETTLED' }), manager)).toEqual([]);
  });

  it('offers nothing when signed out', () => {
    expect(availableClaimActions(claim({}), null)).toEqual([]);
  });
});

describe('canUploadDocuments', () => {
  it('allows owner and staff on open claims, never admins or closed claims', () => {
    expect(canUploadDocuments(claim({}), claimant)).toBe(true);
    expect(canUploadDocuments(claim({ claimantId: 'x' }), claimant)).toBe(false);
    expect(canUploadDocuments(claim({}), officer)).toBe(true);
    expect(canUploadDocuments(claim({}), admin)).toBe(false);
    expect(canUploadDocuments(claim({ status: 'SETTLED' }), officer)).toBe(false);
  });
});
