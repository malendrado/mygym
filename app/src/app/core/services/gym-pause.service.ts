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
  /** Marca opaca de los cierres vigentes o futuros: cambia al crear, editar o levantar uno. Null si el gimnasio está en pausa. */
  closuresStamp?: string | null;
  /** Marca opaca de las reservas vigentes: cambia cuando alguien reserva o cancela (o sea, cuando cambia el cupo de una clase). */
  reservationsStamp?: string | null;
  /** Por qué está en pausa: la apagó mygym a mano (MANUAL) o su plan de mygym está sin activar o impago (BILLING). */
  pauseReason?: 'MANUAL' | 'BILLING' | null;
  /** Con BILLING: NOT_STARTED (aún sin activar) o SUSPENDED (sin pago). */
  billingStatus?: 'NOT_STARTED' | 'SUSPENDED' | null;
}

const RECHECK_MS = 45_000;
const WATCH_MS = 60_000;

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
  private watchTimer: ReturnType<typeof setInterval> | null = null;
  private checking: Promise<void> | null = null;

  /** Solo es no-nulo cuando el gimnasio está en pausa. */
  readonly status = this._status.asReadonly();
  readonly paused = computed(() => this._status()?.paused === true);

  private lastClosuresStamp: string | null = null;
  private readonly _closuresChanged = signal(0);
  /** Sube cada vez que, con la pantalla abierta, cambió algún cierre (creado, editado o levantado): las pantallas recargan sus clases. */
  readonly closuresChanged = this._closuresChanged.asReadonly();

  private lastReservationsStamp: string | null = null;
  private readonly _reservationsChanged = signal(0);
  /** Sube cada vez que, con la pantalla abierta, alguien reservó o canceló: las pantallas recargan los cupos de sus clases. */
  readonly reservationsChanged = this._reservationsChanged.asReadonly();

  /** Consulta el estado (una sola consulta a la vez). Sin sesión o con error de red no cambia nada. */
  check(): Promise<void> {
    this.checking ??= this.load().finally(() => (this.checking = null));
    return this.checking;
  }

  /** El interceptor vio un 403 GYM_PAUSED: pide los datos de contacto y muestra el aviso. */
  markPaused(): void {
    void this.check();
  }

  /**
   * Pantalla abierta con el gimnasio activo: consulta el estado cada 60 s y al volver a la pestaña o a la app, para que la
   * pausa aparezca sola sin tener que tocar nada. Solo con la pestaña visible (una oculta no gasta llamadas) y es el mismo
   * endpoint liviano de siempre. En pausa no hace falta: ya vuelve a consultar cada 45 s (startRechecking).
   */
  watch(): void {
    if (this.watchTimer) {
      return;
    }
    this.watchTimer = setInterval(() => this.checkIfVisible(), WATCH_MS);
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  unwatch(): void {
    if (this.watchTimer) {
      clearInterval(this.watchTimer);
      this.watchTimer = null;
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
  }

  private readonly onVisibilityChange = (): void => this.checkIfVisible();

  private checkIfVisible(): void {
    if (document.visibilityState === 'visible' && !this.paused()) {
      void this.check();
    }
  }

  private async load(): Promise<void> {
    try {
      const status = await firstValueFrom(this.http.get<GymStatus>(`${environment.apiUrl}/api/gym-status`));
      this.trackClosures(status);
      this.trackReservations(status);
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

  /** La primera marca que llega solo se recuerda (la pantalla acaba de cargar los cierres); un cambio posterior avisa. */
  private trackClosures(status: GymStatus): void {
    const stamp = status.closuresStamp ?? null;
    if (stamp === null) {
      return;
    }
    if (this.lastClosuresStamp !== null && stamp !== this.lastClosuresStamp) {
      this._closuresChanged.update((n) => n + 1);
    }
    this.lastClosuresStamp = stamp;
  }

  /** Igual que los cierres: la primera marca solo se recuerda; un cambio posterior avisa que cambió algún cupo. */
  private trackReservations(status: GymStatus): void {
    const stamp = status.reservationsStamp ?? null;
    if (stamp === null) {
      return;
    }
    if (this.lastReservationsStamp !== null && stamp !== this.lastReservationsStamp) {
      this._reservationsChanged.update((n) => n + 1);
    }
    this.lastReservationsStamp = stamp;
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
