import { Claim } from '../models/claim.model';
import { claimantStats, sortByUrgency } from './claim-stats';

const now = Date.parse('2026-11-15T00:00:00Z');

function claim(id: string, overrides: Partial<Claim>): Claim {
  return { id, status: 'SUBMITTED', priority: 'NORMAL', createdAt: '2026-11-14T00:00:00Z', closedAt: null, ...overrides } as Claim;
}

describe('claim stats', () => {
  it('counts claims needing claimant input separately from other open claims', () => {
    const stats = claimantStats([
      claim('a', { status: 'INFO_REQUESTED' }),
      claim('b', { status: 'UNDER_REVIEW' }),
      claim('c', { status: 'SETTLED' }),
    ]);
    expect(stats).toEqual({ total: 3, open: 2, actionRequired: 1, settled: 1 });
  });

  it('sorts by priority, then oldest first', () => {
    const sorted = sortByUrgency(
      [
        claim('new-normal', {}),
        claim('old-normal', { createdAt: '2026-10-01T00:00:00Z' }),
        claim('new-urgent', { priority: 'URGENT' }),
      ],
      now,
    );
    expect(sorted.map((c) => c.id)).toEqual(['new-urgent', 'old-normal', 'new-normal']);
  });
});
