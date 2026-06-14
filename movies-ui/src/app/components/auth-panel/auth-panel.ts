import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService, AuthUser } from '../../services/auth';

@Component({
  standalone: true,
  selector: 'app-auth-panel',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './auth-panel.html',
  styleUrl: './auth-panel.css'
})
export class AuthPanel {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly authenticated$ = this.auth.isAuthenticated$;
  readonly user$ = this.auth.user$;
  errorMessage = '';
  submitting = false;

  readonly loginForm = this.fb.group({
    clientId: ['movies-ui', Validators.required],
    username: ['user', Validators.required],
    password: ['user', Validators.required]
  });

  displayUsername(user: AuthUser): string {
    return user.username || user.email || 'user';
  }

  register(): void {
    this.auth.register();
  }

  login(): void {
    if (this.loginForm.invalid) return;

    const value = this.loginForm.getRawValue();
    this.errorMessage = '';
    this.submitting = true;

    this.auth.login({
      clientId: value.clientId ?? 'movies-ui',
      username: value.username ?? '',
      password: value.password ?? ''
    }).subscribe({
      next: () => {
        this.submitting = false;
      },
      error: err => {
        this.submitting = false;
        this.errorMessage = err?.error?.error_description ?? err?.message ?? 'Login failed';
      }
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
