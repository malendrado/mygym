import { Component, HostListener, effect, inject, signal } from '@angular/core';
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
// medir getBoundingClientRect — no hay evento "scroll terminó" confiable entre navegadores, así
// que se espera un tiempo fijo corto. Mismo criterio que el resto de la app para animaciones
// "ocasionales" (ver framework de decisión de animación): no es momento crítico, un delay fijo
// es aceptable acá.
const SETTLE_DELAY_MS = 220;

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

  protected readonly rect = signal<SpotlightRect | null>(null);
  protected readonly cardPlacement = signal<'below' | 'above'>('below');
  protected readonly cardStyle = signal<{ top?: string; bottom?: string; left: string }>({
    top: '0px',
    left: '0px',
  });
  protected readonly visible = signal(false);
  protected readonly prefersReducedMotion =
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

  private settleTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      // Se re-ejecuta con cada cambio de paso (currentStep es un computed que depende de
      // currentIndex) — reposiciona el spotlight para el elemento real del nuevo paso.
      const step = this.tourService.currentStep();
      if (!step) {
        this.rect.set(null);
        this.visible.set(false);
        return;
      }
      this.visible.set(false);
      step.beforeShow?.();
      this.positionAfterSettle();
    });
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
    clearTimeout(this.settleTimeout);
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
    this.settleTimeout = setTimeout(() => {
      this.measureAndPosition();
      this.visible.set(true);
    }, SETTLE_DELAY_MS);
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

    // 'below' se ancla por `top` (crece hacia abajo desde el borde del recorte); 'above' se
    // ancla por `bottom` (crece hacia arriba) — nunca `top` para 'above', porque la altura de la
    // tarjeta es dinámica (según el largo del texto) y no se puede restar de antemano.
    if (placement === 'below') {
      this.cardStyle.set({
        top: `${spotlight.top + spotlight.height + CARD_GAP}px`,
        left: `${left}px`,
      });
    } else {
      this.cardStyle.set({
        bottom: `${window.innerHeight - spotlight.top + CARD_GAP}px`,
        left: `${left}px`,
      });
    }
  }
}
