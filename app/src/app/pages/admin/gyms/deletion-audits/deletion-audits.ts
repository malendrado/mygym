import { Component, computed, inject, signal } from '@angular/core';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSearchbar,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { documentTextOutline, mailOutline, peopleOutline, searchOutline } from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { GymDeletionAudit } from '../../../../core/models/gym.model';

addIcons({
  'document-text-outline': documentTextOutline,
  'people-outline': peopleOutline,
  'mail-outline': mailOutline,
  'search-outline': searchOutline,
});

type Status = 'loading' | 'loaded' | 'error';

const PAGE_SIZE = 25;

/** Minúsculas y sin tildes, para que "jose" encuentre "José". */
function normalize(value: string | null | undefined): string {
  return (value ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Registro permanente de gimnasios desvinculados — ver GymDeletionAuditRepository en el
 * backend. Deliberadamente sin datos personales de socios (nombre/email/pagos): solo
 * conteos, para probar que el borrado ocurrió, no para reconstruir la clientela de un
 * gimnasio que ya no es cliente. Ver la conversación del 2026-09-23.
 */
@Component({
  selector: 'app-deletion-audits',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonBackButton, IonContent, IonIcon, IonSearchbar, IonSpinner, IonText],
  templateUrl: './deletion-audits.html',
  styleUrl: './deletion-audits.scss',
})
export class DeletionAudits {
  private readonly gymService = inject(GymService);

  protected readonly status = signal<Status>('loading');
  protected readonly audits = signal<GymDeletionAudit[]>([]);

  protected readonly query = signal('');
  private readonly limit = signal(PAGE_SIZE);

  // Busca por nombre o slug del gimnasio, correos de admins, quién ejecutó y la fecha ya formateada.
  private readonly filtered = computed(() => {
    const q = normalize(this.query()).trim();
    if (!q) {
      return this.audits();
    }
    return this.audits().filter((a) =>
      [a.gymName, a.gymSlug, a.adminEmails, a.executedBy, this.formatDate(a.executedAt)].some((v) =>
        normalize(v).includes(q),
      ),
    );
  });
  protected readonly filteredCount = computed(() => this.filtered().length);
  protected readonly visible = computed(() => this.filtered().slice(0, this.limit()));
  protected readonly hasMore = computed(() => this.filtered().length > this.limit());
  protected readonly resultsLabel = computed(() => {
    const n = this.filtered().length;
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
    this.gymService.listDeletionAudits().subscribe({
      next: (audits) => {
        this.audits.set(audits);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  protected formatDate(value: string): string {
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
