import { Injectable, inject } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

// Con el service worker de Angular, una versión nueva se descarga en segundo plano pero la app
// instalada sigue mostrando la vieja hasta una recarga posterior — el socio cerraba y reabría y
// seguía viendo el código anterior (bug real: botón "Cancelar reserva" corregido pero visible).
// Esto activa la versión nueva apenas está lista: recarga al tiro si la app recién abrió (no se
// pierde nada) y, si ya lleva rato en uso, espera a que pase a segundo plano.
const FRESH_START_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly swUpdate = inject(SwUpdate);
  private readonly startedAt = Date.now();

  constructor() {
    if (!this.swUpdate.isEnabled) {
      return;
    }
    this.swUpdate.versionUpdates
      .pipe(filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'))
      .subscribe(() => this.applyWhenSafe());

    this.check();
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.check();
      }
    });
  }

  private check(): void {
    this.swUpdate.checkForUpdate().catch(() => undefined);
  }

  private applyWhenSafe(): void {
    const reload = () => this.swUpdate.activateUpdate().then(() => document.location.reload());
    if (Date.now() - this.startedAt < FRESH_START_MS) {
      void reload();
      return;
    }
    const onHidden = () => {
      if (document.visibilityState === 'hidden') {
        document.removeEventListener('visibilitychange', onHidden);
        void reload();
      }
    };
    document.addEventListener('visibilitychange', onHidden);
  }
}
