export type Role = 'CLAIMANT' | 'ADJUSTER' | 'ADMIN';

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
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
