import { Injectable, computed, inject, signal } from '@angular/core';
import { GymService } from './gym.service';

// Un código por mini-tour de sección (antes 'ADMIN'/'MEMBER' cubrían el panel entero) — ver
// DemoTourService.ALLOWED_TOURS en el backend, tiene que listar exactamente estos mismos
// valores. Prefijo A_/M_ distingue panel admin de panel socio dentro de la misma tabla
// demo_tour_progress (columna tour VARCHAR(10), todos estos códigos entran holgados).
export type TourName =
  | 'A_PLANS'
  | 'A_BLOCKS'
  | 'A_MEMBERS'
  | 'A_GENERAL'
  | 'A_BRANDING'
  | 'A_SCREENS'
  | 'A_CLOSURES'
  | 'A_HISTORY'
  | 'M_RESERVAR'
  | 'M_RESERVAS'
  | 'M_RUTINA';

export interface TourStep {
  title: string;
  body: string;
  /** Selector CSS del elemento real a iluminar — debe existir en el DOM real de la página, nunca
   *  un mockup aparte (mismo criterio ya aplicado en "Ver como socio" de la demo). */
  targetSelector: string;
  /** Hace scroll o ajusta estado local ANTES de que el overlay mida el elemento real — ya no se
   *  usa para cambiar de pestaña (cada mini-tour vive dentro de UNA sola sección), pero queda
   *  disponible por si un paso necesita, por ejemplo, abrir un acordeón antes de iluminarlo. */
  beforeShow?: () => void;
}

/** Un mini-tour de sección: el código que se reporta al backend, la etiqueta corta para el
 *  botón ("Ver tour: Planes") y sus pasos. Compartido entre gym-admin.ts y member.ts. */
export interface SectionTour {
  readonly tour: TourName;
  readonly label: string;
  readonly steps: TourStep[];
}

/** Máquina de estado del tour guiado (spotlight/coach-marks) — el posicionamiento visual vive en
 *  TourOverlay, este servicio solo sabe en qué paso está y reporta progreso al backend
 *  (GymService.recordTourStep, fire-and-forget, nunca bloquea el tour). Un solo servicio para
 *  los dos tours (admin y socio) porque nunca corren a la vez. */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly gymService = inject(GymService);

  private readonly steps = signal<TourStep[]>([]);
  private readonly tour = signal<TourName | null>(null);
  readonly currentIndex = signal(0);

  readonly isOpen = computed(() => this.tour() !== null);
  readonly totalSteps = computed(() => this.steps().length);
  readonly currentStep = computed<TourStep | null>(() => this.steps()[this.currentIndex()] ?? null);
  readonly isFirstStep = computed(() => this.currentIndex() === 0);
  readonly isLastStep = computed(() => this.currentIndex() === this.steps().length - 1);

  start(tour: TourName, steps: TourStep[]): void {
    if (steps.length === 0) {
      return;
    }
    this.tour.set(tour);
    this.steps.set(steps);
    this.currentIndex.set(0);
    this.showCurrent();
  }

  next(): void {
    if (this.isLastStep()) {
      this.finish();
      return;
    }
    this.currentIndex.update((i) => i + 1);
    this.showCurrent();
  }

  back(): void {
    if (this.isFirstStep()) {
      return;
    }
    // No reporta progreso al retroceder — el backend solo guarda el paso MÁS ALTO alcanzado
    // (DemoTourService.recordStepReached), volver atrás no lo hace bajar.
    this.currentIndex.update((i) => i - 1);
  }

  skip(): void {
    this.finish();
  }

  private finish(): void {
    this.tour.set(null);
    this.steps.set([]);
    this.currentIndex.set(0);
  }

  private showCurrent(): void {
    const tour = this.tour();
    if (!tour) {
      return;
    }
    this.gymService.recordTourStep(tour, this.currentIndex() + 1);
  }
}
