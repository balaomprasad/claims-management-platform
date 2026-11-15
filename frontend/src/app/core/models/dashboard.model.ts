import { ClaimStatus } from './claim.model';
import { CurrencyCode, Market } from './reference.model';

export interface DashboardFilter {
  market?: Market | null;
  currency?: CurrencyCode | null;
  submittedFrom?: string | null;
  submittedTo?: string | null;
  ageingDays?: number | null;
}

export interface CurrencyLiability {
  currency: CurrencyCode;
  estimatedExposure: number;
  approvedLiability: number;
  settledAmount: number;
  outstandingClaims: number;
}

export interface MarketSummary {
  market: Market;
  totalClaims: number;
  outstandingClaims: number;
}

export interface OfficerWorkload {
  officerId: string;
  officerName: string;
  openClaims: number;
  decidedClaims: number;
  settledClaims: number;
}

export interface ManagerDashboard {
  generatedAt: string;
  totalClaims: number;
  outstandingClaims: number;
  unassignedClaims: number;
  claimsByStatus: Record<ClaimStatus, number>;
  claimsByMarket: MarketSummary[];
  liabilityByCurrency: CurrencyLiability[];
  officerWorkload: OfficerWorkload[];
  processingTime: { averageDays: number | null; closedClaims: number };
  ageing: { thresholdDays: number; beyondThreshold: number; buckets: { label: string; claims: number }[] };
  definitions: Record<string, string>;
}
