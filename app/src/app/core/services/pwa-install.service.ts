import { Injectable, computed, signal } from '@angular/core';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISSED_KEY = 'mygym.pwaInstallDismissed';
// Marca permanente e independiente de "cerré el aviso" (DISMISSED_KEY): una vez que sabemos con
// certeza que la app está instalada en este dispositivo (corrió standalone, o el propio
// appinstalled disparó), nunca más se vuelve a ofrecer instalar en NINGUNA pestaña normal de
// ese origen — Chrome a veces igual sigue disparando beforeinstallprompt en pestañas sueltas
// aunque el usuario ya haya instalado la app (reportado real: "me sigue ofreciendo instalar de
// nuevo aunque ya la instalé").
const INSTALLED_KEY = 'mygym.pwaInstalled';

// Must be instantiated at bootstrap (see web.config.ts): beforeinstallprompt fires once, early,
// usually before the lazy /member route has loaded.
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private readonly canPrompt = signal(false);
  private readonly dismissed = signal(readDismissed());
  private readonly installed = signal(readInstalled());

  readonly isStandalone =
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;

  // iOS has no install API; Chrome/Firefox on iOS can't add to home screen, only Safari can.
  readonly isIosSafari =
    /iphone|ipad|ipod/i.test(navigator.userAgent) && !/crios|fxios|edgios/i.test(navigator.userAgent);

  // Touch devices only: on desktop Chrome already shows its own install icon in the address bar.
  readonly isTouchDevice = window.matchMedia?.('(pointer: coarse)').matches ?? false;

  readonly mode = computed<'prompt' | 'ios' | null>(() => {
    if (this.isStandalone || this.dismissed() || this.installed() || !this.isTouchDevice) {
      return null;
    }
    if (this.canPrompt()) {
      return 'prompt';
    }
    return this.isIosSafari ? 'ios' : null;
  });

  constructor() {
    // Si esta carga en sí ya es standalone, es la prueba más directa posible de que está
    // instalada — lo dejamos grabado para que una pestaña normal futura en este mismo
    // dispositivo tampoco vuelva a ofrecer instalar.
    if (this.isStandalone) {
      this.markInstalled();
    }
    window.addEventListener('beforeinstallprompt', (event) => {
      // Suppresses Chrome's mini-infobar on the marketing landing; install is offered from /member.
      event.preventDefault();
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this.canPrompt.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canPrompt.set(false);
      this.markInstalled();
    });
  }

  private markInstalled(): void {
    this.installed.set(true);
    try {
      localStorage.setItem(INSTALLED_KEY, '1');
    } catch {
      // Modo privado: no persiste, pero no rompe nada — solo vuelve a preguntar la próxima vez.
    }
  }

  async install(): Promise<void> {
    const promptEvent = this.deferredPrompt;
    if (!promptEvent) {
      return;
    }
    this.deferredPrompt = null;
    this.canPrompt.set(false);
    await promptEvent.prompt();
    await promptEvent.userChoice.catch(() => null);
  }

  dismiss(): void {
    this.dismissed.set(true);
    try {
      localStorage.setItem(DISMISSED_KEY, '1');
    } catch {
      // Private mode: the banner simply comes back next visit.
    }
  }
}

function readDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1';
  } catch {
    return false;
  }
}

function readInstalled(): boolean {
  try {
    return localStorage.getItem(INSTALLED_KEY) === '1';
  } catch {
    return false;
  }
}
