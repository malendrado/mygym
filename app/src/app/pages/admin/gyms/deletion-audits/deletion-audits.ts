import { Component, inject, signal } from '@angular/core';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { documentTextOutline, mailOutline, peopleOutline } from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { GymDeletionAudit } from '../../../../core/models/gym.model';

addIcons({
  'document-text-outline': documentTextOutline,
  'people-outline': peopleOutline,
  'mail-outline': mailOutline,
});

type Status = 'loading' | 'loaded' | 'error';

/**
 * Registro permanente de gimnasios desvinculados — ver GymDeletionAuditRepository en el
 * backend. Deliberadamente sin datos personales de socios (nombre/email/pagos): solo
 * conteos, para probar que el borrado ocurrió, no para reconstruir la clientela de un
 * gimnasio que ya no es cliente. Ver la conversación del 2026-09-23.
 */
@Component({
  selector: 'app-deletion-audits',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonBackButton, IonContent, IonIcon, IonSpinner, IonText],
  templateUrl: './deletion-audits.html',
  styleUrl: './deletion-audits.scss',
})
export class DeletionAudits {
  private readonly gymService = inject(GymService);

  protected readonly status = signal<Status>('loading');
  protected readonly audits = signal<GymDeletionAudit[]>([]);

  constructor() {
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
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }
}
