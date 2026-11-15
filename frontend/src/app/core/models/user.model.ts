export type Role = 'CLAIMANT' | 'CLAIMS_OFFICER' | 'MANAGER' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  CLAIMANT: 'Claimant',
  CLAIMS_OFFICER: 'Claims officer',
  MANAGER: 'Manager',
  ADMIN: 'Administrator',
};

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  market: string;
  phone?: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
