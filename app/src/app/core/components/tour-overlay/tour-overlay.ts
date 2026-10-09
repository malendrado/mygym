import { Component, DestroyRef, HostListener, effect, inject, signal } from '@angular/core';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowBackOutline, arrowForwardOutline, closeOutline } from 'ionicons/icons';
import { TourService } from '../../services/tour.service';

addIcons({
  'arrow-back-outline': arrowBackOutline,
  'arrow-forward-outline': arrowForwardOutline,
  'close-outline': closeOutline,
});

interface SpotlightRect {
  top: number;
  left: number;
  width: number;
  height: number;
}

const SPOTLIGHT_PADDING = 8;
const CARD_GAP = 16;
const VIEWPORT_MARGIN = 16;
// scrollIntoView (disparado por beforeShow o por este componente) necesita terminar antes de
// medir getBoundingClientRect. Un polling con techo fijo (15 intentos / 900ms) parecía
// suficiente para un target cerca de la vista, pero un scroll suave largo (ej. el último socio
// "Sin pago" en una lista de 28) tardó medido hasta 1.3s en asentarse — el techo cortaba antes
// de que terminara y la medición quedaba a mitad de camino (bug real reportado 2026-10-05, el
// hueco del spotlight no coincidía con el botón real). Reemplazado por detección real: un
// listener de 'scroll' en fase de captura (los eventos de scroll no burbujean, pero sí pasan
// por la fase de captura de cualquier ancestro, incluido el scroller interno de ion-content) que
// reinicia un timer corto en cada evento — "asentado" es cuando pasan SETTLE_QUIET_MS sin
// ningún scroll más, sin importar cuánto haya tardado. SETTLE_MAX_WAIT_MS es solo una red de
// seguridad absoluta por si algo generara scroll continuo para siempre.
const SETTLE_QUIET_MS = 120;
const SETTLE_MAX_WAIT_MS = 3000;

/**
 * Overlay tipo spotlight/coach-marks: oscurece la pantalla salvo un recorte exacto sobre el
 * elemento real (nunca un mockup aparte), con una tarjeta de texto al lado. Genérico — no sabe
 * nada de gym-admin ni de member, solo de TourService. Ver plan de implementación para la
 * justificación de la técnica (consultada con ui-ux-pro-max + emil-design-eng).
 */
@Component({
  selector: 'app-tour-overlay',
  imports: [IonButton, IonIcon],
  templateUrl: './tour-overlay.html',
  styleUrl: './tour-overlay.scss',
})
export class TourOverlay {
  protected readonly tourService = inject(TourService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rect = signal<SpotlightRect | null>(null);
  protected readonly cardPlacement = signal<'below' | 'above'>('below');
  protected readonly cardStyle = signal<{ top?: string; bottom?: string; left: string }>({
    top: '0px',
    left: '0px',
  });
  protected readonly visible = signal(false);
  protected readonly prefersReducedMotion =
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

  private quietTimeout?: ReturnType<typeof setTimeout>;
  private maxWaitTimeout?: ReturnType<typeof setTimeout>;
  private onSettleScroll?: () => void;
  private settleScrollEl: HTMLElement | null = null;
  private settleToken = 0;

  constructor() {
    effect(() => {
      // Se re-ejecuta con cada cambio de paso (currentStep es un computed que depende de
      // currentIndex) — reposiciona el spotlight para el elemento real del nuevo paso.
      const step = this.tourService.currentStep();
      if (!step) {
        this.cancelScrollSettle();
        this.rect.set(null);
        this.visible.set(false);
        return;
      }
      this.visible.set(false);
      step.beforeShow?.();
      this.positionAfterSettle();
    });
    // Si el componente se destruye a mitad de un "settle" (el socio navega fuera de la página
    // mientras el tour espera que el scroll se asiente), el timeout/listener quedaban colgando
    // hasta su propio tope de 3s — bajo impacto pero real (auditoría de performance 2026-10-09).
    this.destroyRef.onDestroy(() => this.cancelScrollSettle());
  }

  @HostListener('window:resize')
  protected onResize(): void {
    if (this.tourService.isOpen()) {
      this.measureAndPosition();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.tourService.isOpen()) {
      this.tourService.skip();
    }
  }

  protected next(): void {
    this.tourService.next();
  }

  protected back(): void {
    this.tourService.back();
  }

  protected skip(): void {
    this.tourService.skip();
  }

  private positionAfterSettle(): void {
    this.cancelScrollSettle();
    const step = this.tourService.currentStep();
    if (!step) {
      return;
    }
    const target = document.querySelector(step.targetSelector);
    if (target) {
      target.scrollIntoView({
        block: 'center',
        inline: 'nearest',
        behavior: this.prefersReducedMotion ? 'auto' : 'smooth',
      });
    }
    this.waitForScrollSettle(target);
  }

  // Detecta el fin real del scroll por quietud (sin más eventos 'scroll' durante
  // SETTLE_QUIET_MS), no por un tiempo adivinado — un scroll largo (ej. el último socio "Sin
  // pago" en una lista de 28, o volver a ver el tour una segunda vez desde más abajo en la
  // página) puede tardar más de 1 segundo en asentarse.
  //
  // El primer intento escuchaba 'scroll' en document con useCapture=true, asumiendo que eso
  // alcanzaba para "ver" el scroll interno de ion-content aunque viva en su shadow DOM. Error:
  // el evento 'scroll' tiene composed:false por spec — nunca cruza un shadow boundary, ni
  // siquiera en fase de captura. El listener en document jamás recibía un solo evento real; el
  // único "settle" que disparaba era el timeout de respaldo inicial, que a veces medía a mitad
  // de camino (bug real reportado 2026-10-05: el hueco del spotlight quedaba en otra posición
  // que la tarjeta, sobre todo al volver a ver un tour ya visto una vez, con la página scrolleada
  // desde la vuelta anterior). Fix real: `ion-content.getScrollElement()` devuelve el div
  // scrolleable de verdad (confirmado: sí emite 'scroll' en vivo) — se escucha ahí directo.
  private async waitForScrollSettle(target: Element | null): Promise<void> {
    const token = ++this.settleToken;
    const ionContent = target?.closest('ion-content') as (HTMLElement & { getScrollElement?: () => Promise<HTMLElement> }) | null;
    const scrollEl = (await ionContent?.getScrollElement?.()) ?? null;
    if (token !== this.settleToken) {
      return; // Ya empezó un paso nuevo mientras esperábamos este await — descartar.
    }

    const finish = () => {
      this.cancelScrollSettle();
      this.measureAndPosition();
      this.visible.set(true);
    };
    this.settleScrollEl = scrollEl;
    this.onSettleScroll = () => {
      clearTimeout(this.quietTimeout);
      this.quietTimeout = setTimeout(finish, SETTLE_QUIET_MS);
    };
    scrollEl?.addEventListener('scroll', this.onSettleScroll);
    this.quietTimeout = setTimeout(finish, SETTLE_QUIET_MS);
    this.maxWaitTimeout = setTimeout(finish, SETTLE_MAX_WAIT_MS);
  }

  private cancelScrollSettle(): void {
    ++this.settleToken;
    clearTimeout(this.quietTimeout);
    clearTimeout(this.maxWaitTimeout);
    if (this.onSettleScroll && this.settleScrollEl) {
      this.settleScrollEl.removeEventListener('scroll', this.onSettleScroll);
    }
    this.onSettleScroll = undefined;
    this.settleScrollEl = null;
  }

  private measureAndPosition(): void {
    const step = this.tourService.currentStep();
    if (!step) {
      return;
    }
    const target = document.querySelector(step.targetSelector);
    if (!target) {
      // Elemento no encontrado (gap real entre el paso y la UI, no debería pasar en producción
      // si los selectores están bien) — se deja sin agujero, la tarjeta queda centrada.
      this.rect.set(null);
      this.cardStyle.set({
        top: `${window.innerHeight / 2 - 100}px`,
        left: `${window.innerWidth / 2 - 160}px`,
      });
      return;
    }
    const box = target.getBoundingClientRect();
    const spotlight: SpotlightRect = {
      top: box.top - SPOTLIGHT_PADDING,
      left: box.left - SPOTLIGHT_PADDING,
      width: box.width + SPOTLIGHT_PADDING * 2,
      height: box.height + SPOTLIGHT_PADDING * 2,
    };
    this.rect.set(spotlight);

    const cardWidth = 320;
    const spaceBelow = window.innerHeight - (spotlight.top + spotlight.height);
    const placement: 'below' | 'above' = spaceBelow > 180 ? 'below' : 'above';
    this.cardPlacement.set(placement);

    const left = Math.min(
      Math.max(spotlight.left, VIEWPORT_MARGIN),
      window.innerWidth - cardWidth - VIEWPORT_MARGIN,
    );

    // Altura real medida del DOM (la tarjeta ya existe con opacity:0, position:fixed — su
    // layout no depende de dónde la posicionemos, solo del largo del texto) — con un target muy
    // alto (ej. un formulario largo o una imagen grande) ni 'below' ni 'above' alcanzan a evitar
    // el techo/piso de la ventana, y sin este clamp la tarjeta terminaba cortada a la mitad
    // (bug real reportado 2026-10-05). Fallback a 280px si por algo la tarjeta no midió todavía.
    const cardHeight = (document.querySelector('.tour-card') as HTMLElement | null)?.offsetHeight || 280;

    // 'below' se ancla por `top` (crece hacia abajo desde el borde del recorte); 'above' se
    // ancla por `bottom` (crece hacia arriba).
    if (placement === 'below') {
      const top = Math.min(
        spotlight.top + spotlight.height + CARD_GAP,
        window.innerHeight - cardHeight - VIEWPORT_MARGIN,
      );
      this.cardStyle.set({ top: `${Math.max(top, VIEWPORT_MARGIN)}px`, left: `${left}px` });
    } else {
      const bottom = Math.min(
        window.innerHeight - spotlight.top + CARD_GAP,
        window.innerHeight - VIEWPORT_MARGIN,
      );
      this.cardStyle.set({
        bottom: `${Math.max(bottom, cardHeight + VIEWPORT_MARGIN)}px`,
        left: `${left}px`,
      });
    }
  }
}
