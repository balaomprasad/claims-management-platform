import { CurrencyCode, Market, ProductType } from './reference.model';

export type PolicyStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export interface Policy {
  id: string;
  policyNumber: string;
  holderId: string;
  holderName: string;
  productType: ProductType;
  market: Market;
  currency: CurrencyCode;
  coverageAmount: number;
  deductible: number;
  startDate: string;
  endDate: string;
  status: PolicyStatus;
  createdAt: string;
}
