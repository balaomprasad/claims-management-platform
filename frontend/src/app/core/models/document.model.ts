export type DocumentType =
  | 'PHOTO'
  | 'INVOICE'
  | 'REPAIR_ESTIMATE'
  | 'MEDICAL_REPORT'
  | 'POLICE_REPORT'
  | 'RECEIPT'
  | 'OTHER';

export const DOCUMENT_TYPES: readonly DocumentType[] = [
  'PHOTO',
  'INVOICE',
  'REPAIR_ESTIMATE',
  'MEDICAL_REPORT',
  'POLICE_REPORT',
  'RECEIPT',
  'OTHER',
];

/** Mirrors the server-side checks so users get immediate feedback; the server re-validates file content. */
export const ACCEPTED_DOCUMENT_TYPES = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

export interface ClaimDocument {
  id: string;
  claimId: string;
  documentType: DocumentType;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256: string;
  uploadedById: string;
  uploadedByName: string;
  createdAt: string;
}
