import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/user.model';
import { AuthService } from '../services/auth.service';

/**
 * Allows navigation only for users whose role is listed in the route's {@code data.roles}.
 * This is a UX convenience; the backend remains the authority on authorisation.
 */
export const roleGuard: CanActivateFn = (route) => {
  const allowedRoles = (route.data['roles'] as Role[] | undefined) ?? [];
  const auth = inject(AuthService);
  return allowedRoles.length === 0 || auth.hasRole(...allowedRoles)
    ? true
    : inject(Router).createUrlTree(['/claims']);
};
