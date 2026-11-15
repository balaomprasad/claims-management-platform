import { Role } from '../models/user.model';

/** Landing page for each role after login. */
export const ROLE_HOME: Record<Role, string> = {
  CLAIMANT: '/claimant',
  CLAIMS_OFFICER: '/officer',
  MANAGER: '/manager',
  ADMIN: '/claims',
};
