import { DestroyRef, computed, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { InviteStatus, Member, MemberPageQuery, MembershipStatus, MemberSummary } from '../models/member.model';
import { Page } from '../models/page.model';

/**
 * Estado de la lista paginada de socios (pestaña Socios), compartido por gym-admin (dueño) y
 * gym-form (super-admin) — los dos paneles solo difieren en a qué endpoint le preguntan, que
 * entra como `fetchPage`/`fetchSummary`. Búsqueda, filtros y conteos de las calugas viven en el
 * servidor (la lista ya no está completa en memoria). Debe construirse en un contexto de
 * inyección (inicializador de campo del componente) porque se limpia solo con DestroyRef.
 */
export class MemberListState {
  static readonly PAGE_SIZE = 25;
  private static readonly SEARCH_DEBOUNCE_MS = 300;
  /** Desde cuándo una espera cuenta como "larga". */
  static readonly SLOW_AFTER_MS = 1200;
  /** Cuánto vale una respuesta guardada para volver a mostrarla al instante al repetir un filtro o una página. */
  static readonly CACHE_TTL_MS = 30_000;
  private static readonly CACHE_MAX = 24;

  /** Socios de la página visible (ya filtrados/buscados por el servidor). */
  readonly members = signal<Member[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  /** Hay un pedido en vuelo — la lista se mantiene visible (atenuada), nunca se vacía. */
  readonly loading = signal(false);
  /** La espera ya se nota (más de SLOW_AFTER_MS): la pantalla muestra el loader con logo y pesa encima de la lista. */
  readonly slow = signal(false);
  /** Se está mostrando una respuesta guardada (de hace <30 s) mientras se confirma con el servidor: no hace falta atenuar la lista. */
  readonly stale = signal(false);
  /** Ya llegó al menos una respuesta — antes de eso se muestra el estado "cargando", no "vacío". */
  readonly loaded = signal(false);
  readonly error = signal(false);

  readonly statusFilter = signal<MembershipStatus | null>(null);
  readonly inviteFilter = signal<Exclude<InviteStatus, null> | null>(null);
  /** Texto del buscador (inmediato); el pedido al servidor sale con debounce. */
  readonly searchQuery = signal('');

  readonly summary = signal<MemberSummary | null>(null);
  // Conteos de las calugas: null = todavía no llegan (o fallaron) — la plantilla muestra "—", nunca un 0 falso.
  readonly active = computed(() => this.summary()?.active ?? null);
  readonly expiringSoon = computed(() => this.summary()?.expiringSoon ?? null);
  readonly expired = computed(() => this.summary()?.expired ?? null);
  readonly unpaid = computed(() => this.summary()?.unpaid ?? null);
  readonly invitedPending = computed(() => this.summary()?.invitedPending ?? null);

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / MemberListState.PAGE_SIZE)));
  readonly hasActiveFilter = computed(
    () => !!this.statusFilter() || !!this.inviteFilter() || this.searchQuery().trim() !== '',
  );

  private pageSub?: Subscription;
  private summarySub?: Subscription;
  private summaryInFlight = false;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private slowTimer?: ReturnType<typeof setTimeout>;
  private readonly cache = new Map<string, { at: number; page: Page<Member> }>();

  constructor(
    private readonly fetchPage: (query: MemberPageQuery) => Observable<Page<Member>>,
    private readonly fetchSummary: () => Observable<MemberSummary>,
  ) {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      clearTimeout(this.slowTimer);
      this.pageSub?.unsubscribe();
      this.summarySub?.unsubscribe();
    });
  }

  /** Página actual + conteos — para la primera carga y después de cualquier cambio (pago, baja, alta). */
  reload(): void {
    this.cache.clear();
    this.load();
    this.loadSummary();
  }

  goTo(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  setSearch(value: string): void {
    this.searchQuery.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.goTo(0), MemberListState.SEARCH_DEBOUNCE_MS);
  }

  /**
   * Tras agregar un socio: deja la lista buscando por su email (con los filtros de calugas quitados, que si no
   * podían esconderlo) y refresca página + conteos en un solo pedido. El buscador ya muestra el texto porque
   * se enlaza a searchQuery.
   */
  showNewMember(email: string): void {
    clearTimeout(this.searchTimer);
    this.statusFilter.set(null);
    this.inviteFilter.set(null);
    this.searchQuery.set(email);
    this.page.set(0);
    this.reload();
  }

  // Las calugas son un único grupo de filtro mutuamente excluyente (ver gym-admin.ts): elegir
  // una reemplaza cualquier filtro del otro eje. Al ELEGIR una caluga se borra el texto del buscador
  // (pedido del usuario 2026-10-12): si no, el resultado mezclaba un filtro nuevo con una búsqueda
  // vieja que ya no se acordaba y la lista salía vacía sin explicación. Quitar la caluga no toca el texto.
  setStatusFilter(status: MembershipStatus | null): void {
    this.statusFilter.set(status);
    this.inviteFilter.set(null);
    if (status) {
      this.clearSearchText();
    }
    this.goTo(0);
  }

  setInviteFilter(invite: Exclude<InviteStatus, null> | null): void {
    this.inviteFilter.set(invite);
    this.statusFilter.set(null);
    if (invite) {
      this.clearSearchText();
    }
    this.goTo(0);
  }

  private clearSearchText(): void {
    clearTimeout(this.searchTimer);
    this.searchQuery.set('');
  }

  private load(): void {
    // Un pedido nuevo cancela el anterior — evita que una respuesta lenta pise a una más reciente
    // (ej. escribir rápido en el buscador).
    this.pageSub?.unsubscribe();
    const query: MemberPageQuery = {
      q: this.searchQuery().trim(),
      status: this.statusFilter(),
      invite: this.inviteFilter(),
      page: this.page(),
      size: MemberListState.PAGE_SIZE,
    };
    const key = JSON.stringify(query);
    this.loading.set(true);
    this.error.set(false);
    // Respuesta guardada hace poco: se muestra al instante y se confirma con el servidor por debajo (sin atenuar la lista).
    const cached = this.cache.get(key);
    if (cached && Date.now() - cached.at < MemberListState.CACHE_TTL_MS) {
      this.applyPage(cached.page);
      this.stale.set(true);
    } else {
      this.stale.set(false);
    }
    this.startSlowTimer();
    this.pageSub = this.fetchPage(query).subscribe({
      next: (result) => {
        // Si se borró el último socio de la última página, esa página queda vacía: retroceder una.
        if (result.items.length === 0 && result.page > 0 && result.totalElements > 0) {
          this.cache.delete(key);
          this.goTo(result.page - 1);
          return;
        }
        this.remember(key, result);
        this.applyPage(result);
        this.finishLoading();
      },
      error: () => {
        this.error.set(true);
        this.loaded.set(true);
        this.finishLoading();
      },
    });
  }

  private applyPage(result: Page<Member>): void {
    this.members.set(result.items);
    this.page.set(result.page);
    this.totalElements.set(result.totalElements);
    this.loaded.set(true);
  }

  private remember(key: string, result: Page<Member>): void {
    this.cache.delete(key);
    this.cache.set(key, { at: Date.now(), page: result });
    if (this.cache.size > MemberListState.CACHE_MAX) {
      this.cache.delete(this.cache.keys().next().value as string);
    }
  }

  private startSlowTimer(): void {
    clearTimeout(this.slowTimer);
    this.slow.set(false);
    this.slowTimer = setTimeout(() => this.slow.set(this.loading()), MemberListState.SLOW_AFTER_MS);
  }

  private finishLoading(): void {
    clearTimeout(this.slowTimer);
    this.slow.set(false);
    this.stale.set(false);
    this.loading.set(false);
  }

  private loadSummary(): void {
    this.summarySub?.unsubscribe();
    this.summaryInFlight = true;
    this.summarySub = this.fetchSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryInFlight = false;
      },
      // Los conteos son informativos: si fallan, la lista sigue funcionando (sin toast) y las calugas
      // muestran "—" (o el último valor bueno); ensureSummary() reintenta al volver a abrir la pestaña.
      error: () => {
        this.summaryInFlight = false;
      },
    });
  }

  /** Reintenta los conteos solo si aún no hay datos (falló o no llegó) y no hay un pedido en vuelo. */
  ensureSummary(): void {
    if (this.summary() === null && !this.summaryInFlight) {
      this.loadSummary();
    }
  }
}
