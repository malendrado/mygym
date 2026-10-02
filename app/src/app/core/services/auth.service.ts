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

// Installed PWA: sessionStorage dies every time the app is closed, which would force a Google
// login on every open. In a regular browser tab we keep sessionStorage (shared computers).
// UNA sola vez, no en cada login — bug real reportado: un socio se logueaba, cerraba la app,
// volvía a abrir y la sesión persistía bien (escrita en localStorage); pero si adentro de esa
// MISMA sesión de la app hacía logout y se reloguéaba como otro usuario (ej. super admin), esa
// segunda sesión NO persistía. Sospecha: matchMedia('display-mode: standalone') se evalúa justo
// después de volver del popup/redirect de Google, un momento donde el estado standalone de
// Android puede leerse mal por un instante — evaluarlo una sola vez al arrancar la app (mucho
// antes de que exista cualquier popup de Google) saca esa ventana de carrera por completo.
function isStandaloneApp(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}
const IS_STANDALONE = isStandaloneApp();

function sessionStore(): Storage {
  return IS_STANDALONE ? localStorage : sessionStorage;
}

function isExpired(token: string): boolean {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    // JWTs nunca traen el '=' de relleno de base64 — sin completarlo, atob() puede tirar
    // InvalidCharacterError según el largo exacto del payload (varía con los datos de cada
    // usuario), lo que acá se traducía en "sesión inválida" y un logout silencioso.
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded));
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
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

  /**
   * Awaited on purpose: logout() used to fire signOut() without waiting for it,
   * and callers navigated to the next page immediately after. That left a race
   * where the destination page's authState subscription (login.ts/join.ts) could
   * still see the STALE previous Google user (its idToken still cached in the
   * SDK's BehaviorSubject) and silently re-trigger a sign-in right after the user
   * clicked "Salir" — reported as the page "getting stuck" right after logout.
   * Awaiting here means every caller must `await`/`.then()` before navigating.
   */
  async logout(): Promise<void> {
    this._currentUser.set(null);
    this._token = null;
    sessionStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(STORAGE_KEY);
    // Con un timeout — sin él, signOut() nunca resuelve NI rechaza en varios navegadores
    // mobile y en el WebView de la app instalada (Capacitor): Google migró el SDK a FedCM y
    // ese flujo se queda colgado ahí donde faltan third-party cookies / el permiso
    // "identity-credentials-get" del iframe. El .catch(() => {}) de abajo no alcanza a cubrir
    // eso — un throw se atrapa, un hang no. Nuestra sesión ya quedó limpia arriba, así que
    // reportado real: "logout no hace nada" en mobile — el usuario quedaba pegado en la
    // página esperando este await para siempre.
    await Promise.race([
      this.socialAuthService.signOut().catch(() => {}),
      new Promise<void>((resolve) => setTimeout(resolve, 1500)),
    ]);
  }

  private setSession(response: LoginResponse): void {
    const user: AuthUser = {
      userId: response.userId,
      email: response.email,
      name: response.name,
      role: response.role,
      gymId: response.gymId,
      photoUrl: response.photoUrl,
    };
    this._currentUser.set(user);
    this._token = response.token;
    sessionStore().setItem(STORAGE_KEY, JSON.stringify({ token: response.token, user }));
  }

  // Escanear el QR de la TV (o cualquier link) abre una pestaña normal de Chrome, NO la app
  // instalada — aunque sea el mismo dispositivo con la app ya logueada. Esa pestaña, al no ser
  // standalone, antes solo miraba sessionStorage (vacío, recién abierta) e ignoraba que la
  // sesión real ya estaba en localStorage (mismo origen, la comparten) — pedía login de nuevo
  // sin necesidad. Ahora la LECTURA revisa los dos lugares sin importar el contexto; solo la
  // ESCRITURA de una sesión nueva sigue decidiendo dónde guardar según sessionStore() (para no
  // perder la protección de "computador compartido" en pestañas normales).
  private readStored(): StoredSession | null {
    for (const store of [localStorage, sessionStorage]) {
      const raw = store.getItem(STORAGE_KEY);
      if (!raw) {
        continue;
      }
      try {
        const session = JSON.parse(raw) as StoredSession;
        if (isExpired(session.token)) {
          store.removeItem(STORAGE_KEY);
          continue;
        }
        return session;
      } catch {
        continue;
      }
    }
    return null;
  }
}
