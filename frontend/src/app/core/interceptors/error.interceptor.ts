import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { ApiProblem } from '../models/api.model';
import { AuthService } from '../services/auth.service';

/**
 * Central HTTP error handling: expired/invalid sessions are logged out, and every
 * problem detail is surfaced to the user in a snackbar.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const snackBar = inject(MatSnackBar);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const isLoginCall = request.url.endsWith('/auth/login');
        if (error.status === 401 && !isLoginCall) {
          auth.logout();
        }
        snackBar.open(describeError(error), 'Dismiss', { duration: 5000 });
      }
      return throwError(() => error);
    }),
  );
};

export function describeError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the server. Please try again.';
  }
  const problem = error.error as ApiProblem | null;
  const fieldErrors = problem?.fieldErrors?.map((e) => `${e.field}: ${e.message}`).join('; ');
  return fieldErrors || problem?.detail || problem?.title || `Request failed (${error.status})`;
}

/** Message for page-level error states. */
export function toErrorMessage(error: unknown): string {
  return error instanceof HttpErrorResponse ? describeError(error) : 'Something went wrong.';
}
