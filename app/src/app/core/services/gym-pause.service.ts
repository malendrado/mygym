import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface GymContact {
  whatsappNumber: string | null;
  email: string | null;
  instagramUrl: string | null;
}

/** Espejo de GymStatusController.GymStatusResponse. Con el gimnasio activo no trae datos de contacto. */
export interface GymStatus {
  paused: boolean;
  gymName: string;
  /** Cómo contactar al gimnasio: lo ve un socio. */
  gymContact: GymContact | null;
  /** Cómo contactar a mygym: lo ve el equipo del gimnasio (admin y profesores). */
  mygymContact: GymContact | null;
}

const RECHECK_MS = 45_000;

/**
 * Gimnasio en pausa (Gym.active = false). El servidor deja entrar a socios, admins y profesores pero les rechaza todo lo demás
 * con 403 GYM_PAUSED (GymPauseFilter); la app lo explica con un aviso amable (app-gym-pause) en vez de un error. Mientras
 * está en pausa vuelve a consultar cada 45 s: si el gimnasio se reactiva, la pantalla se recarga sola.
 */
@Injectable({ providedIn: 'root' })
export class GymPauseService {
  private readonly http = inject(HttpClient);
  private readonly _status = signal<GymStatus | null>(null);
  private recheckTimer: ReturnType<typeof setInterval> | null = null;
  private checking: Promise<void> | null = null;

  /** Solo es no-nulo cuando el gimnasio está en pausa. */
  readonly status = this._status.asReadonly();
  readonly paused = computed(() => this._status()?.paused === true);

  /** Consulta el estado (una sola consulta a la vez). Sin sesión o con error de red no cambia nada. */
  check(): Promise<void> {
    this.checking ??= this.load().finally(() => (this.checking = null));
    return this.checking;
  }

  /** El interceptor vio un 403 GYM_PAUSED: pide los datos de contacto y muestra el aviso. */
  markPaused(): void {
    void this.check();
  }

  private async load(): Promise<void> {
    try {
      const status = await firstValueFrom(this.http.get<GymStatus>(`${environment.apiUrl}/api/gym-status`));
      if (status.paused) {
        this._status.set(status);
        this.startRechecking();
      } else if (this._status() !== null) {
        // Se reactivó mientras la pantalla estaba abierta: se recarga para volver a pedir todo desde cero.
        this._status.set(null);
        this.stopRechecking();
        this.reload();
      }
    } catch {
      // sin sesión (super-admin, visitante) o sin red: no hay nada que mostrar
    }
  }

  /** Aparte para poder reemplazarlo en las pruebas (jsdom no implementa recargar la página). */
  protected reload(): void {
    window.location.reload();
  }

  private startRechecking(): void {
    this.recheckTimer ??= setInterval(() => void this.check(), RECHECK_MS);
  }

  private stopRechecking(): void {
    if (this.recheckTimer) {
      clearInterval(this.recheckTimer);
      this.recheckTimer = null;
    }
  }
}
