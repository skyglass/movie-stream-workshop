import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface LoginRequest {
  clientId: string;
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

  get registrationUrl(): string {
    const c = this.cfg.config;
    const redirectUri = encodeURIComponent(c.uiBaseUrl || window.location.origin);
    return `${c.keycloakBaseUrl}/realms/${c.keycloakRealm}/protocol/openid-connect/registrations?client_id=${c.clientId}&response_type=code&scope=openid%20profile%20email&redirect_uri=${redirectUri}`;
  }

  login(request: LoginRequest): Observable<void> {
    const c = this.cfg.config;
    const body = new URLSearchParams();
    body.set('grant_type', 'password');
    body.set('client_id', request.clientId);
    body.set('username', request.username);
    body.set('password', request.password);
    body.set('scope', 'openid profile email movies-api-audience');

    return this.http.post<TokenResponse>(`${c.apiBaseUrl}${c.authTokenPath}`, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).pipe(
      tap(response => {
        localStorage.setItem(this.tokenKey, response.access_token);
        this.userState.next(this.decodeToken(response.access_token));
        this.authState.next(true);
      }),
      map(() => void 0)
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.userState.next(null);
    this.authState.next(false);
  }

  private decodeToken(token: string | null): AuthUser | null {
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
      const payload = JSON.parse(atob(padded));
      const resourceRoles = payload.resource_access?.['movies-ui']?.roles ?? [];
      const realmRoles = payload.realm_access?.roles ?? [];
      return {
        username: payload.preferred_username ?? payload.username ?? payload.sub,
        email: payload.email ?? '',
        roles: [...new Set([...realmRoles, ...resourceRoles])]
      };
    } catch {
      return null;
    }
  }
}
