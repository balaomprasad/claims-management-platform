import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ROLE_HOME } from '../utils/role-home';

/** Sends authenticated users from "/" to the portal for their role. */
export const roleHomeGuard: CanActivateFn = () => {
  const user = inject(AuthService).currentUser();
  return inject(Router).createUrlTree([user ? ROLE_HOME[user.role] : '/login']);
};
