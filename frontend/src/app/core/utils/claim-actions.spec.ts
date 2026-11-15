import { Claim } from '../models/claim.model';
import { User } from '../models/user.model';
import { availableClaimActions } from './claim-actions';

const claimant: User = { id: 'c1', email: 'c@x.io', fullName: 'Casey', role: 'CLAIMANT' };
const adjuster: User = { id: 'a1', email: 'a@x.io', fullName: 'Jordan', role: 'ADJUSTER' };
const otherAdjuster: User = { id: 'a2', email: 'a2@x.io', fullName: 'Sam', role: 'ADJUSTER' };
const admin: User = { id: 'ad1', email: 'ad@x.io', fullName: 'Alex', role: 'ADMIN' };

function claim(overrides: Partial<Claim>): Claim {
  return {
    id: 'claim-1',
    claimNumber: 'CLM-2026-000001',
    policyId: 'p1',
    policyNumber: 'POL-1',
    claimantId: claimant.id,
    claimantName: claimant.fullName,
    assignedAdjusterId: null,
    assignedAdjusterName: null,
    incidentDate: '2026-11-01',
    description: 'Hail damage',
    claimedAmount: 1000,
    approvedAmount: null,
    rejectionReason: null,
    status: 'SUBMITTED',
    createdAt: '2026-11-02T00:00:00Z',
    updatedAt: '2026-11-02T00:00:00Z',
    ...overrides,
  };
}

describe('availableClaimActions', () => {
  it('lets the owning claimant withdraw open claims only', () => {
    expect(availableClaimActions(claim({ status: 'SUBMITTED' }), claimant)).toEqual(['withdraw']);
    expect(availableClaimActions(claim({ status: 'APPROVED' }), claimant)).toEqual([]);
    expect(availableClaimActions(claim({ claimantId: 'someone-else' }), claimant)).toEqual([]);
  });

  it('only offers approve/reject to the assigned adjuster', () => {
    const underReview = claim({ status: 'UNDER_REVIEW', assignedAdjusterId: adjuster.id });
    expect(availableClaimActions(underReview, adjuster)).toEqual(['approve', 'reject']);
    expect(availableClaimActions(underReview, otherAdjuster)).toEqual([]);
  });

  it('offers assign for submitted claims and settle for approved claims to admins', () => {
    expect(availableClaimActions(claim({ status: 'SUBMITTED' }), admin)).toEqual(['assign']);
    expect(availableClaimActions(claim({ status: 'APPROVED' }), admin)).toEqual(['settle']);
    expect(availableClaimActions(claim({ status: 'SETTLED' }), admin)).toEqual([]);
  });

  it('offers nothing when signed out', () => {
    expect(availableClaimActions(claim({}), null)).toEqual([]);
  });
});
