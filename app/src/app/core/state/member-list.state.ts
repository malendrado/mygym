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

  /** Socios de la página visible (ya filtrados/buscados por el servidor). */
  readonly members = signal<Member[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  /** Hay un pedido en vuelo — la lista se mantiene visible (atenuada), nunca se vacía. */
  readonly loading = signal(false);
  /** Ya llegó al menos una respuesta — antes de eso se muestra el estado "cargando", no "vacío". */
  readonly loaded = signal(false);
  readonly error = signal(false);

  readonly statusFilter = signal<MembershipStatus | null>(null);
  readonly inviteFilter = signal<Exclude<InviteStatus, null> | null>(null);
  /** Texto del buscador (inmediato); el pedido al servidor sale con debounce. */
  readonly searchQuery = signal('');

  readonly summary = signal<MemberSummary | null>(null);
  readonly active = computed(() => this.summary()?.active ?? 0);
  readonly expiringSoon = computed(() => this.summary()?.expiringSoon ?? 0);
  readonly expired = computed(() => this.summary()?.expired ?? 0);
  readonly unpaid = computed(() => this.summary()?.unpaid ?? 0);
  readonly invitedPending = computed(() => this.summary()?.invitedPending ?? 0);

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / MemberListState.PAGE_SIZE)));
  readonly hasActiveFilter = computed(
    () => !!this.statusFilter() || !!this.inviteFilter() || this.searchQuery().trim() !== '',
  );

  private pageSub?: Subscription;
  private summarySub?: Subscription;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly fetchPage: (query: MemberPageQuery) => Observable<Page<Member>>,
    private readonly fetchSummary: () => Observable<MemberSummary>,
  ) {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.pageSub?.unsubscribe();
      this.summarySub?.unsubscribe();
    });
  }

  /** Página actual + conteos — para la primera carga y después de cualquier cambio (pago, baja, alta). */
  reload(): void {
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

  // Las calugas son un único grupo de filtro mutuamente excluyente (ver gym-admin.ts): elegir
  // una reemplaza cualquier filtro del otro eje. El buscador de texto se combina con cualquiera.
  setStatusFilter(status: MembershipStatus | null): void {
    this.statusFilter.set(status);
    this.inviteFilter.set(null);
    this.goTo(0);
  }

  setInviteFilter(invite: Exclude<InviteStatus, null> | null): void {
    this.inviteFilter.set(invite);
    this.statusFilter.set(null);
    this.goTo(0);
  }

  private load(): void {
    // Un pedido nuevo cancela el anterior — evita que una respuesta lenta pise a una más reciente
    // (ej. escribir rápido en el buscador).
    this.pageSub?.unsubscribe();
    this.loading.set(true);
    this.error.set(false);
    this.pageSub = this.fetchPage({
      q: this.searchQuery().trim(),
      status: this.statusFilter(),
      invite: this.inviteFilter(),
      page: this.page(),
      size: MemberListState.PAGE_SIZE,
    }).subscribe({
      next: (result) => {
        // Si se borró el último socio de la última página, esa página queda vacía: retroceder una.
        if (result.items.length === 0 && result.page > 0 && result.totalElements > 0) {
          this.goTo(result.page - 1);
          return;
        }
        this.members.set(result.items);
        this.page.set(result.page);
        this.totalElements.set(result.totalElements);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loaded.set(true);
        this.loading.set(false);
      },
    });
  }

  private loadSummary(): void {
    this.summarySub?.unsubscribe();
    this.summarySub = this.fetchSummary().subscribe({
      next: (summary) => this.summary.set(summary),
      // Los conteos son informativos: si fallan, la lista sigue funcionando con los últimos valores.
      error: () => undefined,
    });
  }
}
