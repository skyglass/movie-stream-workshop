import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, map, Observable, of, tap, throwError } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface LoginRequest {
  username: string;
  password: string;
}

interface TokenResponse {
  access_token: string;
  expires_in: number;
  refresh_expires_in?: number;
  token_type: string;
  scope?: string;
}

export interface AuthUser {
  username: string;
  email: string;
  roles: string[];
}

export interface AuthProfile {
  username: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'movies.accessToken';
  private readonly authState = new BehaviorSubject<boolean>(!!localStorage.getItem(this.tokenKey));
  private readonly userState = new BehaviorSubject<AuthUser | null>(this.decodeToken(localStorage.getItem(this.tokenKey)));
  readonly isAuthenticated$ = this.authState.asObservable();
  readonly user$ = this.userState.asObservable();

  constructor(private http: HttpClient, private cfg: AppConfigService) {}

  get token(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  get currentUser(): AuthUser | null {
    return this.userState.value;
  }

  get isAdmin(): boolean {
    return this.currentUser?.roles.includes('MOVIES_ADMIN') ?? false;
  }

  getRegistrationUrl(redirectUrl: string = window.location.href): string {
    const c = this.cfg.config;
    const url = new URL(`${c.keycloakBaseUrl}/realms/${c.keycloakRealm}/protocol/openid-connect/registrations`);
    url.searchParams.set('client_id', c.clientId);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('redirect_uri', this.cleanRedirectUrl(redirectUrl));
    return url.toString();
  }

  register(): void {
    window.location.assign(this.getRegistrationUrl());
  }

  completeLoginRedirect(): Observable<boolean> {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const error = url.searchParams.get('error');

    if (error) {
      const errorDescription = url.searchParams.get('error_description') || error;
      this.clearOidcQueryParams(url);
      return throwError(() => new Error(errorDescription));
    }

    if (!code) {
      return of(false);
    }

    const c = this.cfg.config;
    const redirectUri = this.cleanRedirectUrl(window.location.href);
    const body = new URLSearchParams();
    body.set('grant_type', 'authorization_code');
    body.set('client_id', c.clientId);
    body.set('code', code);
    body.set('redirect_uri', redirectUri);

    return this.http.post<TokenResponse>(`${c.apiBaseUrl}${c.authTokenPath}`, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).pipe(
      tap(response => {
        this.storeToken(response.access_token);
        this.clearOidcQueryParams(url);
      }),
      map(() => true)
    );
  }

  login(request: LoginRequest): Observable<void> {
    const c = this.cfg.config;
    const body = new URLSearchParams();
    body.set('grant_type', 'password');
    body.set('client_id', c.clientId);
    body.set('username', request.username);
    body.set('password', request.password);

    return this.http.post<TokenResponse>(`${c.apiBaseUrl}${c.authTokenPath}`, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).pipe(
      tap(response => {
        this.storeToken(response.access_token);
      }),
      map(() => void 0)
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.userState.next(null);
    this.authState.next(false);
  }

  applyProfile(profile: AuthProfile): void {
    const current = this.userState.value;
    if (!current) return;

    this.userState.next({
      ...current,
      username: this.firstNonBlank(profile.username, current.username, current.email),
      email: this.firstNonBlank(profile.email, current.email)
    });
  }

  private decodeToken(token: string | null): AuthUser | null {
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
      const payload = JSON.parse(atob(padded));
      const resourceRoles = payload.resource_access?.['movies-ui']?.roles ?? [];
      const realmRoles = payload.realm_access?.roles ?? [];
      const email = this.firstNonBlank(payload.email);
      return {
        username: this.firstNonBlank(
          payload.preferred_username,
          payload.username,
          payload.name,
          email ? email.split('@')[0] : '',
          payload.sub
        ),
        email,
        roles: [...new Set([...realmRoles, ...resourceRoles])]
      };
    } catch {
      return null;
    }
  }

  private firstNonBlank(...values: Array<string | null | undefined>): string {
    return values.find(value => !!value && value.trim().length > 0)?.trim() ?? '';
  }

  private storeToken(accessToken: string): void {
    localStorage.setItem(this.tokenKey, accessToken);
    this.userState.next(this.decodeToken(accessToken));
    this.authState.next(true);
  }

  private cleanRedirectUrl(redirectUrl: string): string {
    const url = new URL(redirectUrl);
    ['code', 'state', 'session_state', 'iss', 'error', 'error_description'].forEach(param => url.searchParams.delete(param));
    return url.toString();
  }

  private clearOidcQueryParams(url: URL): void {
    ['code', 'state', 'session_state', 'iss', 'error', 'error_description'].forEach(param => url.searchParams.delete(param));
    window.history.replaceState(window.history.state, document.title, `${url.pathname}${url.search}${url.hash}`);
  }
}
