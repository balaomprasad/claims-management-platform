import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, switchMap, tap } from 'rxjs';
import { API_BASE_URL } from '../models/api.model';
import { LoginRequest, LoginResponse, RegisterRequest, Role, User } from '../models/user.model';

interface StoredSession {
  token: string;
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'claimsphere.session';

/**
 * Owns the authenticated session. The token lives in sessionStorage (cleared when the tab closes)
 * and the session is dropped automatically when the JWT expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<StoredSession | null>(this.restoreSession());
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly currentUser = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  constructor() {
    this.scheduleExpiry();
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => this.startSession(response)),
      map((response) => response.user),
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<User>(`${API_BASE_URL}/auth/register`, request)
      .pipe(switchMap(() => this.login({ email: request.email, password: request.password })));
  }

  logout(redirect = true): void {
    clearTimeout(this.expiryTimer);
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  private startSession(response: LoginResponse): void {
    const session: StoredSession = {
      token: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
      user: response.user,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
    this.scheduleExpiry();
  }

  private restoreSession(): StoredSession | null {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      const session = JSON.parse(raw) as StoredSession;
      return session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }

  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    const session = this.session();
    if (session) {
      this.expiryTimer = setTimeout(() => this.logout(), session.expiresAt - Date.now());
    }
  }
}
