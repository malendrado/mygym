import { DestroyRef, computed, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { Page } from '../models/page.model';

export interface PagedQuery {
  q: string;
  page: number;
  size: number;
}

/**
 * Estado genérico de una lista paginada con búsqueda (gimnasios del super-admin, auditoría de
 * desvinculaciones, administradores...). Dos modos de navegación:
 *  - 'pages': tabla de gestión con páginas numeradas — `items` es solo la página visible.
 *  - 'append': feed/registro con "Cargar más" — `items` acumula las páginas ya pedidas.
 * La búsqueda es del servidor (con debounce) y un pedido nuevo cancela el anterior. Debe
 * construirse en un contexto de inyección (inicializador de campo) porque se limpia solo con
 * DestroyRef. La lista de socios tiene filtros propios y usa MemberListState.
 */
export class PagedListState<T> {
  private static readonly SEARCH_DEBOUNCE_MS = 300;

  readonly items = signal<T[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  readonly hasNext = signal(false);
  /** Hay un pedido en vuelo — la lista se mantiene visible (atenuada), nunca se vacía. */
  readonly loading = signal(false);
  /** Ya llegó al menos una respuesta — antes de eso se muestra "cargando", no "vacío". */
  readonly loaded = signal(false);
  readonly error = signal(false);
  /** Texto del buscador (inmediato); el pedido al servidor sale con debounce. */
  readonly searchQuery = signal('');

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / this.pageSize)));

  private sub?: Subscription;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly fetch: (query: PagedQuery) => Observable<Page<T>>,
    private readonly pageSize: number,
    private readonly mode: 'pages' | 'append' = 'pages',
  ) {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.sub?.unsubscribe();
    });
  }

  /** Vuelve a pedir la página actual (modo 'pages') o empieza de cero (modo 'append'). */
  reload(): void {
    if (this.mode === 'append') {
      this.page.set(0);
    }
    this.load();
  }

  goTo(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  /** Solo modo 'append': pide la página siguiente y la agrega al final. */
  loadMore(): void {
    if (this.loading() || !this.hasNext()) {
      return;
    }
    this.page.update((p) => p + 1);
    this.load();
  }

  setSearch(value: string): void {
    this.searchQuery.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.goTo(0), PagedListState.SEARCH_DEBOUNCE_MS);
  }

  private load(): void {
    this.sub?.unsubscribe();
    this.loading.set(true);
    this.error.set(false);
    const requestedPage = this.page();
    this.sub = this.fetch({ q: this.searchQuery().trim(), page: requestedPage, size: this.pageSize }).subscribe({
      next: (result) => {
        // Si se borró el último elemento de la última página, esa página queda vacía: retroceder una.
        if (this.mode === 'pages' && result.items.length === 0 && result.page > 0 && result.totalElements > 0) {
          this.goTo(result.page - 1);
          return;
        }
        this.items.update((current) => (this.mode === 'append' && result.page > 0 ? [...current, ...result.items] : result.items));
        this.page.set(result.page);
        this.totalElements.set(result.totalElements);
        this.hasNext.set(result.hasNext);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => {
        // En 'append' un "Cargar más" fallido no debe perder el avance de página.
        if (this.mode === 'append' && requestedPage > 0) {
          this.page.set(requestedPage - 1);
        }
        this.error.set(true);
        this.loaded.set(true);
        this.loading.set(false);
      },
    });
  }
}
