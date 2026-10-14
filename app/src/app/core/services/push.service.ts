import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PwaInstallService } from './pwa-install.service';

/**
 * - `loading`: todavía no se sabe.
 * - `unsupported`: este navegador no puede (o el servidor no tiene push configurado): no se muestra nada.
 * - `needs-install`: iPhone en Safari: solo recibe push con la app agregada a la pantalla de inicio.
 * - `default`: puede activar y todavía no decidió.
 * - `denied`: bloqueó los avisos en los ajustes del teléfono (el sitio no puede volver a pedirlo).
 * - `enabled`: este dispositivo recibe avisos.
 */
export type PushState = 'loading' | 'unsupported' | 'needs-install' | 'default' | 'denied' | 'enabled';
/**
 * Por qué falló activar los avisos:
 * - `no-push-service`: el navegador no tiene servicio de notificaciones (ej. teléfono sin servicios de Google).
 * - `not-ready`: la app todavía no terminó de prepararse (service worker sin activar).
 * - `server`: nuestro servidor rechazó o no pudo guardar la suscripción.
 * - `unsupported`: el servidor no tiene push configurado o este navegador no puede.
 * - `unknown`: cualquier otra cosa; `detail` lleva el nombre del error para diagnosticar.
 */
export type PushFailure = 'no-push-service' | 'not-ready' | 'server' | 'unsupported' | 'unknown';
export type PushEnableResult = { status: 'ok' } | { status: 'denied' } | { status: 'error'; reason: PushFailure; detail: string };

const DISMISSED_UNTIL_KEY = 'mygym.pushPromptDismissedUntil';
const DISMISS_DAYS = 14;
const LOGOUT_TIMEOUT_MS = 1500;

interface SubscriptionBody {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

/**
 * Avisos push (ver docs/plans/2026-10-13-notificaciones-push.md). El permiso del teléfono se pide SIEMPRE desde un toque
 * del usuario (enable()), nunca al cargar la página. La suscripción pertenece al usuario con sesión: al cerrar sesión se
 * borra de este teléfono (releaseDevice) y al iniciar sesión otra persona se reasigna (syncOwnership).
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly swPush = inject(SwPush);
  private readonly pwa = inject(PwaInstallService);
  private readonly base = `${environment.apiUrl}/api/push`;

  private readonly _state = signal<PushState>('loading');
  private readonly _busy = signal(false);
  private readonly dismissedUntil = signal(readDismissedUntil());
  private serverKey: string | null = null;
  private refreshing: Promise<void> | null = null;

  readonly state = this._state.asReadonly();
  /** Hay una activación o desactivación en curso (para no disparar dos a la vez). */
  readonly busy = this._busy.asReadonly();
  /** El usuario cerró el aviso ("Ahora no") hace menos de 14 días. */
  readonly dismissed = computed(() => this.dismissedUntil() > Date.now());
  /**
   * Hay que ofrecer activar los avisos. SOLO dentro de la app instalada y abierta como app (display-mode: standalone):
   * nunca en una pestaña del navegador, ni en el celular ni en el computador. Única excepción: iPhone en Safari, donde se
   * muestra una guía para instalarla primero (sin eso iOS no permite avisos).
   */
  readonly canOffer = computed(() => {
    const state = this._state();
    if (state === 'needs-install') {
      return true;
    }
    return this.pwa.isStandalone && (state === 'default' || state === 'denied');
  });

  /** Calcula el estado de este dispositivo. Seguro de llamar varias veces (comparte la consulta en curso). */
  refresh(): Promise<void> {
    this.refreshing ??= this.compute().finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  /** Activa los avisos en este dispositivo. Llamar solo desde un toque del usuario. */
  async enable(): Promise<PushEnableResult> {
    this._busy.set(true);
    try {
      return await this.doEnable();
    } finally {
      this._busy.set(false);
    }
  }

  private async doEnable(): Promise<PushEnableResult> {
    let step: 'subscribe' | 'register' = 'subscribe';
    try {
      const key = await this.loadServerKey();
      if (!key) {
        this._state.set('unsupported');
        return { status: 'error', reason: 'unsupported', detail: 'sin-clave' };
      }
      const subscription = await this.swPush.requestSubscription({ serverPublicKey: key });
      step = 'register';
      await this.register(subscription.toJSON() as SubscriptionBody);
      this._state.set('enabled');
      // Aviso de prueba: confirma de punta a punta que el teléfono recibe. Su fallo no cancela la activación.
      this.http.post<void>(`${this.base}/test`, {}).subscribe({ error: () => undefined });
      return { status: 'ok' };
    } catch (error) {
      if (typeof Notification !== 'undefined' && Notification.permission === 'denied') {
        this._state.set('denied');
        return { status: 'denied' };
      }
      console.warn('[push] no se pudo activar', step, error);
      return classifyFailure(step, error);
    }
  }

  /** Desactiva los avisos en este dispositivo (borra la suscripción aquí y en el servidor). */
  async disable(): Promise<void> {
    this._busy.set(true);
    try {
      await this.dropSubscription();
      this._state.set('default');
    } finally {
      this._busy.set(false);
    }
  }

  /** Al cerrar sesión: este teléfono deja de recibir los avisos de quien salió. No debe trabar el cierre. */
  async releaseDevice(): Promise<void> {
    if (!this.swPush.isEnabled) {
      return;
    }
    await Promise.race([
      this.dropSubscription().catch(() => undefined),
      new Promise<void>((resolve) => setTimeout(resolve, LOGOUT_TIMEOUT_MS)),
    ]);
    this._state.set('loading');
  }

  /** Con sesión iniciada y avisos ya activos en este teléfono: la suscripción pasa a pertenecer al usuario actual. */
  async syncOwnership(): Promise<void> {
    await this.refresh();
    if (this._state() !== 'enabled') {
      return;
    }
    try {
      const subscription = await firstValueFrom(this.swPush.subscription);
      if (subscription) {
        await this.register(subscription.toJSON() as SubscriptionBody);
      }
    } catch {
      // best-effort: si falla, el próximo inicio de la app lo reintenta
    }
  }

  dismiss(): void {
    const until = Date.now() + DISMISS_DAYS * 24 * 60 * 60 * 1000;
    this.dismissedUntil.set(until);
    try {
      localStorage.setItem(DISMISSED_UNTIL_KEY, String(until));
    } catch {
      // sin almacenamiento: se oculta solo en esta sesión
    }
  }

  // ---------------------------------------------------------------------------------------

  private async compute(): Promise<void> {
    const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
    if (isIos && !this.pwa.isStandalone) {
      this._state.set('needs-install');
      return;
    }
    if (!this.swPush.isEnabled || typeof Notification === 'undefined' || !('PushManager' in window)) {
      this._state.set('unsupported');
      return;
    }
    if (Notification.permission === 'denied') {
      this._state.set('denied');
      return;
    }
    if (!(await this.loadServerKey())) {
      this._state.set('unsupported'); // el servidor no tiene push configurado
      return;
    }
    if (Notification.permission === 'granted') {
      const subscription = await firstValueFrom(this.swPush.subscription).catch(() => null);
      this._state.set(subscription ? 'enabled' : 'default');
      return;
    }
    this._state.set('default');
  }

  private async loadServerKey(): Promise<string | null> {
    if (this.serverKey) {
      return this.serverKey;
    }
    try {
      const response = await firstValueFrom(this.http.get<{ publicKey: string } | null>(`${this.base}/public-key`));
      this.serverKey = response?.publicKey ?? null;
    } catch {
      this.serverKey = null;
    }
    return this.serverKey;
  }

  private register(body: SubscriptionBody): Promise<unknown> {
    return firstValueFrom(
      this.http.post<void>(`${this.base}/subscriptions`, { endpoint: body.endpoint, keys: body.keys }),
    );
  }

  private async dropSubscription(): Promise<void> {
    if (!this.swPush.isEnabled) {
      return;
    }
    const subscription = await firstValueFrom(this.swPush.subscription).catch(() => null);
    if (!subscription) {
      return;
    }
    const endpoint = subscription.endpoint;
    // Primero el servidor (con la sesión aún válida), luego el navegador.
    await firstValueFrom(this.http.request<void>('DELETE', `${this.base}/subscriptions`, { body: { endpoint } })).catch(
      () => undefined,
    );
    await this.swPush.unsubscribe().catch(() => undefined);
  }
}

/** Qué le decimos a la persona según por qué falló; "unknown" y "server" llevan un código corto para poder diagnosticar. */
export function pushFailureMessage(reason: PushFailure, detail: string): string {
  switch (reason) {
    case 'no-push-service':
      return 'Este teléfono no puede recibir avisos: necesita los servicios de Google (los Huawei nuevos no los traen). Los correos te siguen llegando igual.';
    case 'not-ready':
      return 'La app todavía se está preparando. Espera unos segundos e inténtalo de nuevo.';
    case 'server':
      return `No pudimos registrar este teléfono en el servidor. Inténtalo de nuevo (código: ${detail}).`;
    case 'unsupported':
      return 'Este teléfono o navegador no admite avisos desde esta app.';
    default:
      return `No pudimos activar los avisos (código: ${detail}).`;
  }
}

/** Traduce el error del navegador o del servidor a una causa que la app sabe explicar. */
function classifyFailure(step: 'subscribe' | 'register', error: unknown): PushEnableResult {
  const name = error instanceof Error ? error.name : typeof error;
  const message = error instanceof Error ? error.message : String(error);
  if (error instanceof HttpErrorResponse) {
    return { status: 'error', reason: 'server', detail: `HTTP ${error.status}` };
  }
  if (step === 'register') {
    return { status: 'error', reason: 'server', detail: name };
  }
  if (name === 'AbortError' || /push service|registration failed/i.test(message)) {
    return { status: 'error', reason: 'no-push-service', detail: name };
  }
  if (/no active service worker|service worker/i.test(message) || name === 'InvalidStateError') {
    return { status: 'error', reason: 'not-ready', detail: name };
  }
  if (name === 'NotSupportedError') {
    return { status: 'error', reason: 'unsupported', detail: name };
  }
  return { status: 'error', reason: 'unknown', detail: name };
}

function readDismissedUntil(): number {
  try {
    return Number(localStorage.getItem(DISMISSED_UNTIL_KEY)) || 0;
  } catch {
    return 0;
  }
}
