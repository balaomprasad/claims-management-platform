export type ProductType = 'MOTOR' | 'HEALTH' | 'TRAVEL' | 'PROPERTY';
export type PolicyStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export interface Policy {
  id: string;
  policyNumber: string;
  holderId: string;
  holderName: string;
  productType: ProductType;
  coverageAmount: number;
  deductible: number;
  startDate: string;
  endDate: string;
  status: PolicyStatus;
  createdAt: string;
}
