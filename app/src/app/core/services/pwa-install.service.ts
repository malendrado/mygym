import { Injectable, computed, signal } from '@angular/core';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISSED_KEY = 'mygym.pwaInstallDismissed';

// Must be instantiated at bootstrap (see web.config.ts): beforeinstallprompt fires once, early,
// usually before the lazy /member route has loaded.
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private readonly canPrompt = signal(false);
  private readonly dismissed = signal(readDismissed());

  readonly isStandalone =
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;

  // iOS has no install API; Chrome/Firefox on iOS can't add to home screen, only Safari can.
  readonly isIosSafari =
    /iphone|ipad|ipod/i.test(navigator.userAgent) && !/crios|fxios|edgios/i.test(navigator.userAgent);

  readonly mode = computed<'prompt' | 'ios' | null>(() => {
    if (this.isStandalone || this.dismissed()) {
      return null;
    }
    if (this.canPrompt()) {
      return 'prompt';
    }
    return this.isIosSafari ? 'ios' : null;
  });

  constructor() {
    window.addEventListener('beforeinstallprompt', (event) => {
      // Suppresses Chrome's mini-infobar on the marketing landing; install is offered from /member.
      event.preventDefault();
      this.deferredPrompt = event as BeforeInstallPromptEvent;
      this.canPrompt.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canPrompt.set(false);
    });
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
