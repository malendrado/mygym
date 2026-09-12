import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';
import { ReservationService } from '../../core/services/reservation.service';
import { GymBlockOccurrence, Reservation } from '../../core/models/reservation.model';

type Status = 'idle' | 'loading' | 'error';

@Component({
  selector: 'app-member',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonList, IonItem, IonLabel, IonBadge, IonText],
  templateUrl: './member.html',
  styleUrl: './member.scss',
})
export class MemberPage {
  private readonly authService = inject(AuthService);
  private readonly reservationService = inject(ReservationService);
  private readonly router = inject(Router);

  protected readonly status = signal<Status>('idle');
  protected readonly occurrences = signal<GymBlockOccurrence[]>([]);
  protected readonly myReservations = signal<Reservation[]>([]);

  constructor() {
    this.loadOccurrences();
    this.loadMyReservations();
  }

  protected book(occurrence: GymBlockOccurrence): void {
    this.reservationService
      .book({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: () => {
          this.loadOccurrences();
          this.loadMyReservations();
        },
        error: () => this.status.set('error'),
      });
  }

  protected cancel(reservationId: number): void {
    this.reservationService.cancel(reservationId).subscribe({
      next: () => {
        this.loadOccurrences();
        this.loadMyReservations();
      },
      error: () => this.status.set('error'),
    });
  }

  protected logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private loadOccurrences(): void {
    this.status.set('loading');
    const from = new Date();
    const to = new Date();
    to.setDate(to.getDate() + 7);
    this.reservationService.listOccurrences(this.toIsoDate(from), this.toIsoDate(to)).subscribe({
      next: (occurrences) => {
        this.occurrences.set(occurrences);
        this.status.set('idle');
      },
      error: () => this.status.set('error'),
    });
  }

  private loadMyReservations(): void {
    this.reservationService.myReservations().subscribe({
      next: (reservations) => this.myReservations.set(reservations),
      error: () => this.status.set('error'),
    });
  }

  private toIsoDate(date: Date): string {
    return date.toISOString().slice(0, 10);
  }
}
