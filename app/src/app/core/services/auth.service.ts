import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { SocialAuthService } from '@abacritt/angularx-social-login';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, LoginResponse } from '../models/auth.model';

const STORAGE_KEY = 'mygym.auth';

interface StoredSession {
  token: string;
  user: AuthUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly base = `${environment.apiUrl}/api/auth`;
  private readonly publicGymsBase = `${environment.apiUrl}/api/public/gyms`;

  private readonly stored = this.readStored();
  private readonly _currentUser = signal<AuthUser | null>(this.stored?.user ?? null);
  private _token: string | null = this.stored?.token ?? null;

  readonly currentUser = this._currentUser.asReadonly();

  get token(): string | null {
    return this._token;
  }

  loginWithGoogle(idToken: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.base}/google`, { idToken })
      .pipe(tap((response) => this.setSession(response)));
  }

  /** Public self-signup for a specific gym's join page — provisions a MEMBER of that gym on first login. */
  joinGym(idToken: string, gymSlug: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.publicGymsBase}/${gymSlug}/join`, { idToken })
      .pipe(tap((response) => this.setSession(response)));
  }

  logout(): void {
    this._currentUser.set(null);
    this._token = null;
    sessionStorage.removeItem(STORAGE_KEY);
    // Also clears the SDK's own cached Google session — otherwise its authState
    // observable still holds the last signed-in user and immediately re-fires on
    // the next page that subscribes to it (e.g. /login), silently logging back in.
    this.socialAuthService.signOut().catch(() => {});
  }

  private setSession(response: LoginResponse): void {
    const user: AuthUser = {
      userId: response.userId,
      email: response.email,
      name: response.name,
      role: response.role,
      gymId: response.gymId,
    };
    this._currentUser.set(user);
    this._token = response.token;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: response.token, user }));
  }

  private readStored(): StoredSession | null {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as StoredSession;
    } catch {
      return null;
    }
  }
}
