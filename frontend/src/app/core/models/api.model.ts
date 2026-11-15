export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 7807 problem detail returned by the backend for every error. */
export interface ApiProblem {
  title?: string;
  status: number;
  detail?: string;
  errorCode?: string;
  fieldErrors?: { field: string; message: string }[];
}

export const API_BASE_URL = '/api/v1';
