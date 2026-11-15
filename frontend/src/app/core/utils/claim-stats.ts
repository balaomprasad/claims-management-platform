import { Claim, claimAgeDays, isTerminal } from '../models/claim.model';

export interface ClaimantStats {
  total: number;
  open: number;
  actionRequired: number;
  settled: number;
}

export function claimantStats(claims: Claim[]): ClaimantStats {
  return {
    total: claims.length,
    open: claims.filter((c) => !isTerminal(c.status)).length,
    actionRequired: claims.filter((c) => c.status === 'INFO_REQUESTED').length,
    settled: claims.filter((c) => c.status === 'SETTLED').length,
  };
}

/** Highest priority first, then oldest, so officers see the claims most at risk of breaching service levels. */
export function sortByUrgency(claims: Claim[], now = Date.now()): Claim[] {
  const weight = { URGENT: 3, HIGH: 2, NORMAL: 1, LOW: 0 } as const;
  return [...claims].sort(
    (a, b) => weight[b.priority] - weight[a.priority] || claimAgeDays(b, now) - claimAgeDays(a, now),
  );
}
