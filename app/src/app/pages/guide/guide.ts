import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Meta } from '@angular/platform-browser';
import { IonContent, IonIcon, IonSpinner } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  addCircleOutline,
  checkmark,
  chevronBackOutline,
  chevronForwardOutline,
  downloadOutline,
  ellipsisVertical,
  shareOutline,
} from 'ionicons/icons';
import { GymService } from '../../core/services/gym.service';
import { PwaInstallService } from '../../core/services/pwa-install.service';
import { GuideInfo } from '../../core/models/gym.model';
import { deriveSurfaceTint, ensureMinContrastColor, syncThemeOverrides } from '../../core/utils/gym-theme';
import { toLogoImgSrc } from '../../core/utils/logo-src';

addIcons({
  'add-circle-outline': addCircleOutline,
  checkmark,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'download-outline': downloadOutline,
  'ellipsis-vertical': ellipsisVertical,
  'share-outline': shareOutline,
});

type GuideStatus = 'loading' | 'ready' | 'invalid';
type SlideKind = 'install' | 'book' | 'cancel' | 'checkin' | 'pay' | 'plan' | 'history';

interface Slide {
  kind: SlideKind;
  title: string;
  caption: string;
}

// Una idea por pantalla, en el orden en que el socio las va a necesitar. Los textos son a
// propósito cortos y sin jerga: la guía es para todas las edades.
const SLIDES: readonly Slide[] = [
  {
    kind: 'install',
    title: 'Ten tu gimnasio en el celular',
    caption: 'Instálala una vez y queda como cualquier otra app, con su propio ícono.',
  },
  {
    kind: 'book',
    title: 'Reserva tu clase en 3 toques',
    caption: 'Elige el día, toca la clase y listo. Sin escribir por WhatsApp ni esperar respuesta.',
  },
  {
    kind: 'cancel',
    title: '¿No puedes ir? Avisa a tiempo',
    caption:
      'Cancela con la anticipación que define tu gimnasio y la clase no se descuenta. Si una clase está llena, entras a la lista de espera.',
  },
  {
    kind: 'checkin',
    title: 'Marca tu asistencia con el QR',
    caption: 'Al llegar, escanea el código que muestra la pantalla del gimnasio. Se hace en un segundo.',
  },
  {
    kind: 'pay',
    title: 'Paga o renueva tu plan',
    caption: 'Elige tu plan y págalo desde la app. Cuando se acabe, renuévalo en el mismo lugar.',
  },
  {
    kind: 'plan',
    title: 'Mira tu plan de un vistazo',
    caption: 'Cuántas clases te quedan y hasta cuándo dura tu plan, siempre al día.',
  },
  {
    kind: 'history',
    title: 'Tu historial y tu rutina',
    caption: 'Revisa a qué clases fuiste y anota lo que entrenaste. Tu progreso queda guardado.',
  },
];

// Umbral de arrastre horizontal para pasar de pantalla (px) — más chico que eso es un toque.
const SWIPE_MIN_PX = 48;

/**
 * "Guía del socio": página pública pero no abierta — solo se entra con el enlace firmado del
 * correo de bienvenida (/guia/:token). Formato historias (una idea por pantalla), con la marca
 * del gym de quien la abre. Sin reproducción automática a propósito: cada persona avanza a su
 * ritmo (botones grandes, toque, deslizar o flechas del teclado).
 */
@Component({
  selector: 'app-guide',
  imports: [IonContent, IonIcon, IonSpinner, RouterLink],
  templateUrl: './guide.html',
  styleUrl: './guide.scss',
})
export class GuidePage {
  private readonly route = inject(ActivatedRoute);
  private readonly gymService = inject(GymService);
  private readonly meta = inject(Meta);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly pwa = inject(PwaInstallService);

  protected readonly slides = SLIDES;
  protected readonly status = signal<GuideStatus>('loading');
  protected readonly info = signal<GuideInfo | null>(null);
  // La pantalla final (CTA) es el índice slides.length.
  protected readonly index = signal(this.initialIndex());
  protected readonly lastIndex = SLIDES.length;

  protected readonly gym = computed(() => this.info()?.gym ?? null);
  protected readonly firstName = computed(() => this.info()?.firstName ?? '');
  protected readonly themeSurface = computed(() =>
    deriveSurfaceTint(this.gym()?.themeColor ?? '#c6ff3d', this.gym()?.themeMode),
  );
  protected readonly accentTextSafe = computed(() =>
    ensureMinContrastColor(this.gym()?.themeColor ?? '#c6ff3d', this.themeSurface().card),
  );
  protected readonly logoSrc = computed(() => toLogoImgSrc(this.gym()?.logoSvg));
  // Solo se promete lo que ese gimnasio realmente tiene configurado.
  protected readonly payCaption = computed(() =>
    this.gym()?.flowConfigured
      ? 'Elige tu plan y págalo con tarjeta o por transferencia, directo desde la app. Cuando se acabe, renuévalo en el mismo lugar.'
      : 'Elige tu plan y págalo por transferencia desde la app. Cuando se acabe, renuévalo en el mismo lugar.',
  );
  protected readonly onLastScreen = computed(() => this.index() === this.lastIndex);
  protected readonly stepLabel = computed(() =>
    this.onLastScreen() ? 'Listo' : `Paso ${this.index() + 1} de ${SLIDES.length}`,
  );

  private touchStartX: number | null = null;

  constructor() {
    // La guía es solo para quien recibió el correo: que no aparezca en buscadores.
    this.meta.addTag({ name: 'robots', content: 'noindex, nofollow' });
    this.destroyRef.onDestroy(() => this.meta.removeTag("name='robots'"));

    const token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!token) {
      this.status.set('invalid');
    } else {
      this.gymService.getGuide(token).subscribe({
        next: (info) => {
          this.info.set(info);
          this.status.set('ready');
        },
        error: () => this.status.set('invalid'),
      });
    }

    // Misma mecánica que join/member: el modo claro necesita los tokens globales, no solo bg/card.
    effect(() => {
      const gym = this.gym();
      syncThemeOverrides(gym?.themeMode, gym?.themeColor);
    });
    this.destroyRef.onDestroy(() => syncThemeOverrides('DARK', null));
  }

  // ?p=3 abre directo en la pantalla 3 — para que el gym pueda mandar por WhatsApp solo la que
  // el socio necesita (ej. "cómo cancelar").
  private initialIndex(): number {
    const raw = Number(this.route.snapshot.queryParamMap.get('p'));
    return Number.isInteger(raw) && raw >= 1 && raw <= SLIDES.length ? raw - 1 : 0;
  }

  protected next(): void {
    this.index.update((i) => Math.min(i + 1, this.lastIndex));
  }

  protected back(): void {
    this.index.update((i) => Math.max(i - 1, 0));
  }

  protected skip(): void {
    this.index.set(this.lastIndex);
  }

  protected restart(): void {
    this.index.set(0);
  }

  protected goTo(i: number): void {
    this.index.set(Math.min(Math.max(i, 0), this.lastIndex));
  }

  /** Posición relativa al slide activo: -1 anterior, 0 activo, 1 siguiente (para el desplazamiento). */
  protected offset(i: number): number {
    return Math.sign(i - this.index());
  }

  protected installNow(): void {
    void this.pwa.install();
  }

  @HostListener('window:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowRight') {
      this.next();
    } else if (event.key === 'ArrowLeft') {
      this.back();
    }
  }

  protected onPointerDown(event: PointerEvent): void {
    this.touchStartX = event.clientX;
  }

  protected onPointerUp(event: PointerEvent): void {
    if (this.touchStartX === null) {
      return;
    }
    const delta = event.clientX - this.touchStartX;
    this.touchStartX = null;
    if (delta <= -SWIPE_MIN_PX) {
      this.next();
    } else if (delta >= SWIPE_MIN_PX) {
      this.back();
    }
  }
}
