import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginResponse } from '../models/user.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const response: LoginResponse = {
    accessToken: 'token-123',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: { id: 'u1', email: 'casey@example.com', fullName: 'Casey', role: 'CLAIMANT' },
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stores the session after login and exposes the user role', () => {
    service.login({ email: 'casey@example.com', password: 'secret123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(response);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('token-123');
    expect(service.hasRole('CLAIMANT')).toBe(true);
    expect(service.hasRole('ADMIN')).toBe(false);
    expect(sessionStorage.getItem('claimsphere.session')).toContain('token-123');
  });

  it('clears the session on logout', () => {
    service.login({ email: 'casey@example.com', password: 'secret123' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(response);

    service.logout(false);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(sessionStorage.getItem('claimsphere.session')).toBeNull();
  });
});
