import { Directive, ElementRef, OnDestroy, afterNextRender, computed, inject, input, signal } from '@angular/core';

/** Un tamaño posible de celda, en rem. w = 0 → fila de ancho completo (una sola columna). */
export interface FitTier {
  name: string;
  w: number;
  h: number;
  gap: number;
}

const EMPTY_TIER: FitTier = { name: 'none', w: 0, h: 1, gap: 0 };

export interface FitLayout {
  tier: FitTier;
  /** Índice del primer elemento de la página actual. */
  start: number;
  /** Cuántos elementos reales renderizar (desde start). */
  visible: number;
  /** Total de páginas (1 = entran todos, sin carrusel). */
  pages: number;
  /** Página actual, 0-based. */
  page: number;
}

/**
 * Grilla que se ajusta al espacio REAL disponible, medido con ResizeObserver: elige el tamaño
 * de celda más grande (de `tvFitTiers`, ordenados de mayor a menor) en el que entran todos los
 * elementos; si ni el más chico alcanza, usa el más chico y PAGINA (carrusel): muestra una
 * página por vez según `tvFitPage` (el reloj compartido de la pantalla) — nadie queda escondido
 * tras un "+N" (decisión del usuario). Así el contenido nunca se corta (una TV no tiene scroll)
 * ni deja huecos vacíos cuando sobra lugar, en vez de adivinar topes fijos por combinación.
 *
 * El host tiene que tener un alto definido por el layout (ej. flex: 1 1 0 + min-height en el
 * SCSS + contain: size), nunca por su propio contenido, o la medición sería circular.
 */
@Directive({
  selector: '[tvFit]',
  standalone: true,
  exportAs: 'tvFit',
  host: {
    '[style.display]': '"grid"',
    '[style.grid-template-columns]': 'columns()',
    '[style.grid-auto-rows]': 'layout().tier.h + "rem"',
    '[style.gap]': 'layout().tier.gap + "rem"',
    '[style.align-content]': 'align()',
    // Sin min-height acá a propósito: un estilo inline le ganaría a los mínimos que define el
    // SCSS de quien usa la directiva (ej. "al menos una fila" en la columna lateral).
    '[style.overflow]': '"hidden"',
    '[attr.data-tier]': 'layout().tier.name',
  },
})
export class TvFitDirective implements OnDestroy {
  // No son input.required a propósito: los host bindings (que leen layout()) pueden evaluarse
  // antes de que llegue el primer valor → NG0950. Con defaults, la primera pasada simplemente
  // renderiza 0 elementos y la siguiente ya tiene los valores reales.
  readonly count = input<number>(0, { alias: 'tvFit' });
  readonly tiers = input<FitTier[]>([], { alias: 'tvFitTiers' });
  // true (roster): si hay que paginar, una celda se usa para el indicador "1/3". false (agenda):
  // todas las celdas muestran elementos reales y el indicador lo muestra quien usa la
  // directiva (ej. en el título) — con poco espacio, una celda de indicador le quita una fila útil.
  readonly reserveSlot = input(true, { alias: 'tvFitReserveSlot' });
  // Tick del carrusel (se usa módulo cantidad de páginas).
  readonly page = input(0, { alias: 'tvFitPage' });
  readonly align = input<'start' | 'center'>('start', { alias: 'tvFitAlign' });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly size = signal({ w: 0, h: 0 });
  private observer: ResizeObserver | null = null;

  readonly layout = computed<FitLayout>(() => {
    const tiers = this.tiers().length ? this.tiers() : [EMPTY_TIER];
    const n = this.count();
    const { w, h } = this.size();
    const rem = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;

    let chosen = tiers[tiers.length - 1];
    let capacity = this.capacity(chosen, w, h, rem);
    for (const tier of tiers) {
      const c = this.capacity(tier, w, h, rem);
      if (c >= n) {
        chosen = tier;
        capacity = c;
        break;
      }
    }
    if (capacity >= n) return { tier: chosen, start: 0, visible: n, pages: 1, page: 0 };
    if (capacity <= 0) return { tier: chosen, start: 0, visible: 0, pages: 1, page: 0 };
    const perPageMax = Math.max(this.reserveSlot() ? capacity - 1 : capacity, 1);
    const pages = Math.ceil(n / perPageMax);
    // Páginas parejas (7 en páginas de 5 → 4 + 3, no 5 + 2): ninguna queda casi vacía.
    const perPage = Math.ceil(n / pages);
    const page = this.page() % pages;
    const start = page * perPage;
    return { tier: chosen, start, visible: Math.min(perPage, n - start), pages, page };
  });

  protected readonly columns = computed(() => {
    const tier = this.layout().tier;
    return tier.w === 0 ? '1fr' : `repeat(auto-fill, minmax(${tier.w}rem, 1fr))`;
  });

  constructor() {
    afterNextRender(() => {
      this.observer = new ResizeObserver(([entry]) => {
        const rect = entry.contentRect;
        this.size.set({ w: rect.width, h: rect.height });
      });
      this.observer.observe(this.host.nativeElement);
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  // Misma cuenta que hace el navegador con repeat(auto-fill, minmax(w, 1fr)): columnas que
  // entran contando los gaps, por filas de alto fijo que entran contando los gaps.
  private capacity(tier: FitTier, w: number, h: number, rem: number): number {
    if (w <= 0 || h <= 0) return 0;
    const gap = tier.gap * rem;
    const cols = tier.w === 0 ? 1 : Math.floor((w + gap) / (tier.w * rem + gap));
    const rows = Math.floor((h + gap) / (tier.h * rem + gap));
    return Math.max(cols, 0) * Math.max(rows, 0);
  }
}
