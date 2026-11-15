import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ROLE_HOME } from '../../core/utils/role-home';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Bound from the ?returnUrl= query parameter set by the auth guard. */
  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => void this.router.navigateByUrl(this.safeReturnUrl() ?? ROLE_HOME[user.role]),
      error: () => this.submitting.set(false),
    });
  }

  private safeReturnUrl(): string | null {
    const url = this.returnUrl();
    // Only in-app relative paths are honoured, preventing open redirects.
    return url && url.startsWith('/') && !url.startsWith('//') && url !== '/' ? url : null;
  }
}
