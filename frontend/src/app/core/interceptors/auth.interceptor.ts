import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '../models/api.model';
import { AuthService } from '../services/auth.service';

/** Attaches the bearer token to API calls only, never to third-party URLs. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthService).token();
  if (!token || !request.url.startsWith(API_BASE_URL)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
