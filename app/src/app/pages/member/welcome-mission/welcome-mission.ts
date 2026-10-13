import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  checkmark,
  closeOutline,
  downloadOutline,
  qrCodeOutline,
  shareOutline,
} from 'ionicons/icons';
import { PwaInstallService } from '../../../core/services/pwa-install.service';

addIcons({
  'calendar-outline': calendarOutline,
  checkmark,
  'close-outline': closeOutline,
  'download-outline': downloadOutline,
  'qr-code-outline': qrCodeOutline,
  'share-outline': shareOutline,
});

const HIDDEN_KEY = 'mygym.missionHidden';

type StepId = 'install' | 'book' | 'checkin';

interface MissionStep {
  id: StepId;
  title: string;
  hint: string;
  cta: string;
  icon: string;
  done: boolean;
}

/**
 * "Misión de bienvenida": 3 pasos (instalar, reservar, primer check-in) con el avance real del
 * socio. Reservar y check-in se deducen de datos del servidor; instalar es una marca local del
 * dispositivo. Solo aparece si falta algo — o para celebrar, una vez, si lo completó en esta
 * sesión — y el socio puede ocultarla cuando quiera (nunca bloquea nada).
 */
@Component({
  selector: 'app-welcome-mission',
  imports: [IonIcon],
  templateUrl: './welcome-mission.html',
  styleUrl: './welcome-mission.scss',
})
export class WelcomeMission {
  protected readonly pwa = inject(PwaInstallService);

  /** false mientras el dashboard no llega: evita mostrar pasos "pendientes" que luego se marcan solos. */
  readonly ready = input(false);
  readonly gymName = input('');
  readonly hasBooked = input(false);
  readonly hasCheckedIn = input(false);

  readonly bookClick = output<void>();
  readonly checkinClick = output<void>();

  private readonly hidden = signal(readHidden());
  // Verdadero si en esta sesión se vio la misión incompleta: solo entonces se celebra al terminar.
  private readonly sawIncomplete = signal(false);
  protected readonly iosHintOpen = signal(false);

  protected readonly steps = computed<MissionStep[]>(() => {
    const list: MissionStep[] = [];
    // Instalar solo tiene sentido en celular/tablet; en escritorio el paso no existe.
    if (this.pwa.isTouchDevice) {
      list.push({
        id: 'install',
        title: 'Instala la app',
        hint:
          this.pwa.canInstallNatively() || this.pwa.isIosSafari
            ? 'Tenla en tu pantalla de inicio, a un toque.'
            : 'Abre el menú ⋮ del navegador y elige Instalar app.',
        cta: this.pwa.canInstallNatively() ? 'Instalar' : this.pwa.isIosSafari ? 'Ver cómo' : '',
        icon: 'download-outline',
        done: this.pwa.isInstalled(),
      });
    }
    list.push(
      {
        id: 'book',
        title: 'Reserva tu primera clase',
        hint: 'Elige un día y toca la clase que quieras.',
        cta: 'Reservar',
        icon: 'calendar-outline',
        done: this.hasBooked(),
      },
      {
        id: 'checkin',
        title: 'Marca tu primera asistencia',
        hint: 'Al llegar, escanea el QR de la pantalla del gimnasio.',
        cta: 'Escanear QR',
        icon: 'qr-code-outline',
        done: this.hasCheckedIn(),
      },
    );
    return list;
  });

  protected readonly doneCount = computed(() => this.steps().filter((s) => s.done).length);
  protected readonly allDone = computed(() => this.doneCount() === this.steps().length);
  protected readonly progress = computed(() => this.doneCount() / this.steps().length);

  protected readonly visible = computed(() => {
    if (!this.ready() || this.hidden()) {
      return false;
    }
    return !this.allDone() || this.sawIncomplete();
  });

  constructor() {
    effect(() => {
      if (this.ready() && !this.allDone()) {
        this.sawIncomplete.set(true);
      }
    });
  }

  protected act(step: MissionStep): void {
    if (step.done) {
      return;
    }
    if (step.id === 'install') {
      if (this.pwa.canInstallNatively()) {
        void this.pwa.install();
      } else {
        this.iosHintOpen.update((open) => !open);
      }
    } else if (step.id === 'book') {
      this.bookClick.emit();
    } else {
      this.checkinClick.emit();
    }
  }

  protected hide(): void {
    this.hidden.set(true);
    try {
      localStorage.setItem(HIDDEN_KEY, '1');
    } catch {
      // Modo privado: vuelve a aparecer en la próxima visita, sin romper nada.
    }
  }
}

function readHidden(): boolean {
  try {
    return localStorage.getItem(HIDDEN_KEY) === '1';
  } catch {
    return false;
  }
}
