import { Component, inject, signal } from '@angular/core';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonLabel,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { peopleOutline, shieldCheckmarkOutline } from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { Admin, GymAdminListItem } from '../../../../core/models/gym.model';

addIcons({
  'shield-checkmark-outline': shieldCheckmarkOutline,
  'people-outline': peopleOutline,
});

type Status = 'loading' | 'loaded' | 'error';
type Tab = 'super' | 'gym';

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
    IonBackButton,
    IonContent,
    IonIcon,
    IonSpinner,
    IonText,
    IonSegment,
    IonSegmentButton,
    IonLabel,
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

  constructor() {
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
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }
}
