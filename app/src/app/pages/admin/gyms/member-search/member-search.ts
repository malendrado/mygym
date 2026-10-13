import { Component, ElementRef, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { IonIcon, IonSpinner } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline, searchOutline } from 'ionicons/icons';
import { Member } from '../../../../core/models/member.model';
import { MemberService } from '../../../../core/services/member.service';

addIcons({
  'close-outline': closeOutline,
  'search-outline': searchOutline,
});

/** Lo mínimo de un socio que necesitan las pestañas para mostrar "su" información. */
export interface MemberRef {
  id: number;
  name: string;
  photoUrl: string | null;
}

const MIN_LENGTH = 2;
const DEBOUNCE_MS = 250;
const MAX_RESULTS = 8;

/**
 * Buscador de socios compartido de Clases (Agenda y Asistencia): se escribe un nombre, se elige de la lista y ese socio
 * queda como una etiqueta que se puede quitar; las dos pestañas muestran entonces la información de ese mismo socio.
 *
 * Es un combobox: teclado completo (↑ ↓ Enter Escape), resultados anunciados a lectores de pantalla y zonas táctiles de
 * 44px. Compartido por gym-admin y gym-form (`gymId` null = gym del JWT; un id = super-admin sobre ese gym). Usa la misma
 * búsqueda de socios de la pestaña Socios (servidor): no hay endpoint propio.
 */
@Component({
  selector: 'app-member-search',
  imports: [IonIcon, IonSpinner],
  templateUrl: './member-search.html',
  styleUrl: './member-search.scss',
})
export class MemberSearch {
  private readonly memberService = inject(MemberService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  /** El socio elegido: mientras exista se muestra su etiqueta en vez del campo de búsqueda. */
  @Input() selected: MemberRef | null = null;
  @Output() readonly picked = new EventEmitter<MemberRef>();
  @Output() readonly cleared = new EventEmitter<void>();

  protected readonly query = signal('');
  protected readonly results = signal<Member[]>([]);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  /** true cuando `results` corresponde a la consulta actual (no a una anterior). */
  protected readonly settled = signal(false);
  protected readonly focused = signal(false);
  protected readonly active = signal(-1);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private blurTimer: ReturnType<typeof setTimeout> | null = null;
  private requestId = 0;

  protected readonly trimmed = computed(() => this.query().trim());
  protected readonly open = computed(() => this.focused() && this.trimmed().length > 0);
  protected readonly tooShort = computed(() => this.trimmed().length > 0 && this.trimmed().length < MIN_LENGTH);
  protected readonly activeId = computed(() => (this.active() >= 0 ? `ms-opt-${this.active()}` : null));
  /** Lo que lee un lector de pantalla cuando cambian los resultados (sin mover el foco). */
  protected readonly liveText = computed(() => {
    if (!this.open() || this.tooShort() || this.loading() || !this.settled()) {
      return '';
    }
    if (this.failed()) {
      return 'No pudimos buscar socios.';
    }
    const n = this.results().length;
    return n === 0 ? 'Sin resultados.' : `${n} ${n === 1 ? 'socio encontrado' : 'socios encontrados'}.`;
  });

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.active.set(-1);
    this.schedule();
  }

  private schedule(): void {
    if (this.timer) {
      clearTimeout(this.timer);
    }
    const q = this.trimmed();
    if (q.length < MIN_LENGTH) {
      this.requestId++;
      this.results.set([]);
      this.loading.set(false);
      this.failed.set(false);
      this.settled.set(false);
      return;
    }
    this.settled.set(false);
    this.timer = setTimeout(() => this.search(q), DEBOUNCE_MS);
  }

  protected retry(): void {
    const q = this.trimmed();
    if (q.length >= MIN_LENGTH) {
      this.search(q);
    }
  }

  private search(q: string): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.failed.set(false);
    const query = { q, status: null, invite: null, page: 0, size: MAX_RESULTS };
    const request = this.gymId === null ? this.memberService.listPage(query) : this.memberService.listPageForGym(this.gymId, query);
    request.subscribe({
      next: (page) => {
        if (id !== this.requestId) {
          return; // llegó tarde: ya se escribió otra cosa
        }
        this.results.set(page.items);
        this.active.set(page.items.length > 0 ? 0 : -1);
        this.loading.set(false);
        this.settled.set(true);
      },
      error: () => {
        if (id !== this.requestId) {
          return;
        }
        this.results.set([]);
        this.failed.set(true);
        this.loading.set(false);
        this.settled.set(true);
      },
    });
  }

  protected onKey(event: KeyboardEvent): void {
    const count = this.results().length;
    switch (event.key) {
      case 'ArrowDown':
        if (count > 0) {
          event.preventDefault();
          this.active.set((this.active() + 1) % count);
        }
        break;
      case 'ArrowUp':
        if (count > 0) {
          event.preventDefault();
          this.active.set((this.active() - 1 + count) % count);
        }
        break;
      case 'Enter': {
        const member = this.results()[this.active()];
        if (this.open() && member) {
          event.preventDefault();
          this.pick(member);
        }
        break;
      }
      case 'Escape':
        if (this.trimmed()) {
          event.preventDefault();
          this.clearQuery();
        }
        break;
    }
  }

  protected onFocus(): void {
    if (this.blurTimer) {
      clearTimeout(this.blurTimer);
    }
    this.focused.set(true);
  }

  protected onBlur(): void {
    // Un clic en un resultado no debe cerrar la lista antes de registrarse (los resultados evitan el blur con mousedown).
    this.blurTimer = setTimeout(() => this.focused.set(false), 120);
  }

  protected pick(member: Member): void {
    this.picked.emit({ id: member.id, name: member.name, photoUrl: member.photoUrl });
    this.clearQuery();
    this.focused.set(false);
  }

  protected clearQuery(): void {
    this.query.set('');
    this.results.set([]);
    this.active.set(-1);
    this.settled.set(false);
    this.failed.set(false);
    this.loading.set(false);
    this.requestId++;
    if (this.timer) {
      clearTimeout(this.timer);
    }
  }

  protected clearSelection(): void {
    this.cleared.emit();
    // Al quitar el filtro el foco vuelve al campo de búsqueda (el usuario suele querer buscar a otro).
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('.ms-input')?.focus(), 0);
  }
}
