import { Component, computed, inject, signal } from '@angular/core';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonLabel,
  IonSearchbar,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { peopleOutline, searchOutline, shieldCheckmarkOutline } from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { Admin, GymAdminListItem } from '../../../../core/models/gym.model';

addIcons({
  'shield-checkmark-outline': shieldCheckmarkOutline,
  'people-outline': peopleOutline,
  'search-outline': searchOutline,
});

type Status = 'loading' | 'loaded' | 'error';
type Tab = 'super' | 'gym';

const PAGE_SIZE = 25;

/** Minúsculas y sin tildes, para que "jose" encuentre "José". */
function normalize(value: string | null | undefined): string {
  return (value ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Vista de solo lectura "Administradores" — pedido explícito del usuario (2026-10-05): poder ver
 * quiénes son super-admin y qué gym-admin pertenece a cada gimnasio, sin abrir cada ficha de
 * gimnasio una por una. Deliberadamente sin alta/edición/borrado acá — eso sigue viviendo donde
 * ya vivía (ficha de cada gimnasio, o alta manual directa en la base para un super-admin nuevo).
 */
@Component({
  selector: 'app-admins-list',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonBackButton,
    IonContent,
    IonIcon,
    IonSpinner,
    IonText,
    IonSegment,
    IonSegmentButton,
    IonLabel,
    IonSearchbar,
  ],
  templateUrl: './admins-list.html',
  styleUrl: './admins-list.scss',
})
export class AdminsList {
  private readonly gymService = inject(GymService);

  protected readonly tab = signal<Tab>('super');
  protected readonly status = signal<Status>('loading');
  protected readonly superAdmins = signal<Admin[]>([]);
  protected readonly gymAdmins = signal<GymAdminListItem[]>([]);

  protected readonly query = signal('');
  private readonly limit = signal(PAGE_SIZE);

  private readonly filteredSuper = computed(() => {
    const q = normalize(this.query()).trim();
    if (!q) {
      return this.superAdmins();
    }
    return this.superAdmins().filter((a) => normalize(a.name).includes(q) || normalize(a.email).includes(q));
  });

  private readonly filteredGym = computed(() => {
    const q = normalize(this.query()).trim();
    if (!q) {
      return this.gymAdmins();
    }
    return this.gymAdmins().filter(
      (a) => normalize(a.name).includes(q) || normalize(a.email).includes(q) || normalize(a.gymName).includes(q),
    );
  });

  /** Lista de la pestaña activa ya filtrada; el total y el recorte salen de ella. */
  protected readonly filteredCount = computed(() =>
    this.tab() === 'super' ? this.filteredSuper().length : this.filteredGym().length,
  );
  protected readonly visibleSuper = computed(() => this.filteredSuper().slice(0, this.limit()));
  protected readonly visibleGym = computed(() => this.filteredGym().slice(0, this.limit()));
  protected readonly hasMore = computed(() => this.filteredCount() > this.limit());
  protected readonly resultsLabel = computed(() => {
    const n = this.filteredCount();
    return n === 1 ? '1 resultado' : `${n} resultados`;
  });

  constructor() {
    this.load();
  }

  protected setQuery(value: string | null | undefined): void {
    this.query.set(value ?? '');
    this.limit.set(PAGE_SIZE);
  }

  protected clearQuery(): void {
    this.setQuery('');
  }

  protected showMore(): void {
    this.limit.update((n) => n + PAGE_SIZE);
  }

  protected load(): void {
    this.status.set('loading');
    this.gymService.listSuperAdmins().subscribe({
      next: (admins) => this.superAdmins.set(admins),
      error: () => this.status.set('error'),
    });
    this.gymService.listAllGymAdmins().subscribe({
      next: (admins) => {
        this.gymAdmins.set(admins);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  protected setTab(tab: Tab): void {
    this.tab.set(tab);
    this.limit.set(PAGE_SIZE);
  }

  protected initials(name: string): string {
    const parts = name.trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase();
  }

  protected formatDate(value: string | null): string {
    if (!value) {
      return 'Nunca entró';
    }
    return new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }
}
