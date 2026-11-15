import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Role } from '../models/user.model';
import { AuthService } from '../services/auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  let currentRole: Role | null;

  beforeEach(() => {
    currentRole = null;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            hasRole: (...roles: Role[]) => currentRole !== null && roles.includes(currentRole),
            currentUser: () => (currentRole ? { id: 'u1', email: 'u@x.io', fullName: 'U', role: currentRole } : null),
          },
        },
      ],
    });
  });

  function run(roles: Role[]): boolean | UrlTree {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() =>
      roleGuard(route, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  it('allows users with a permitted role', () => {
    currentRole = 'CLAIMANT';
    expect(run(['CLAIMANT'])).toBe(true);
  });

  it('redirects users without a permitted role to their own portal', () => {
    currentRole = 'CLAIMS_OFFICER';
    const result = run(['CLAIMANT']);
    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/officer');
  });
});
