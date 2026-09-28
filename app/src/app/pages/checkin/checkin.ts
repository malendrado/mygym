import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IonButton, IonContent, IonIcon, IonSpinner } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { alertCircleOutline, checkmarkCircleOutline } from 'ionicons/icons';
import { ReservationService } from '../../core/services/reservation.service';

type Status = 'checking' | 'success' | 'error';

/**
 * A donde llega el socio al escanear el QR de asistencia que muestra la TV del gym (ver
 * tv-screen.ts) — sin pantalla intermedia, confirma automáticamente apenas carga. authGuard ya
 * la protege (ver web.routes.ts): si el socio no tenía sesión iniciada, Google login lo trae de
 * vuelta acá mismo gracias a returnUrl de Angular Router.
 */
@Component({
  selector: 'app-checkin',
  imports: [IonContent, IonButton, IonIcon, IonSpinner],
  templateUrl: './checkin.html',
  styleUrl: './checkin.scss',
})
export class CheckinPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reservationService = inject(ReservationService);

  protected readonly status = signal<Status>('checking');
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly classLabels = signal<string[]>([]);
  protected readonly classLabelText = computed(() => this.classLabels().join(' y '));

  constructor() {
    addIcons({ checkmarkCircleOutline, alertCircleOutline });
    const code = this.route.snapshot.paramMap.get('code');
    if (!code) {
      this.status.set('error');
      this.errorMessage.set('Falta el código de la clase.');
      return;
    }
    this.reservationService.checkIn(code).subscribe({
      next: (res) => {
        this.classLabels.set(res.classLabels);
        this.status.set('success');
      },
      error: (err) => {
        this.status.set('error');
        this.errorMessage.set(err?.message ?? 'No pudimos confirmar tu asistencia.');
      },
    });
  }

  protected goToMember(): void {
    this.router.navigateByUrl('/member');
  }
}
