import { HttpInterceptorFn } from '@angular/common/http';
import { API_BASE_URL } from '../models/api.model';

/** Tags every API call with a fresh X-Correlation-Id so a UI action can be traced through backend logs and events. */
export const correlationInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_BASE_URL) || request.headers.has('X-Correlation-Id')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));
};
