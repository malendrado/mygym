import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  arrowBackOutline,
  calendarOutline,
  checkmarkDoneOutline,
  eyeOutline,
  lockClosedOutline,
} from 'ionicons/icons';
import { DemoPreviewService } from '../../core/services/demo-preview.service';
import { GymBlockOccurrence, Reservation } from '../../core/models/reservation.model';
import { MemberPlan, PublicGym } from '../../core/models/gym.model';
import { Member, MembershipStatus } from '../../core/models/member.model';

addIcons({
  'arrow-back-outline': arrowBackOutline,
  'calendar-outline': calendarOutline,
  'checkmark-done-outline': checkmarkDoneOutline,
  'eye-outline': eyeOutline,
  'lock-closed-outline': lockClosedOutline,
});

function shortTime(time: string): string {
  return time.slice(0, 5);
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "YYYY-MM-DD" → "mié 23 sep" — mismo criterio que member.ts (nunca `new Date(isoString)` directo). */
function formatShortDate(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  return capitalize(new Intl.DateTimeFormat('es-CL', { weekday: 'short', day: 'numeric', month: 'short' }).format(date));
}

function todayIsoInGymZone(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}

/**
 * "Ver como socio" del toggle de la demo comercial (Role.DEMO_ADMIN) — vista de solo lectura del
 * socio de muestra fijo del gym demo (ver DemoPreviewController/DemoPreviewService). No es un
 * clon 1:1 de member.ts: muestra lo esencial que un prospecto necesita ver (su plan, cupos,
 * próximas clases, historial), sin la UX de adquisición (calendario mensual, quotes, etc.) que
 * no aporta nada acá. Nunca reserva ni cancela — no hay endpoints de escritura que llamar.
 */
@Component({
  selector: 'app-demo-preview',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon, IonBadge, IonSpinner],
  templateUrl: './demo-preview.html',
  styleUrl: './demo-preview.scss',
})
export class DemoPreview {
  private readonly demoPreviewService = inject(DemoPreviewService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loaded = signal(false);
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly membership = signal<Member | null>(null);
  protected readonly plans = signal<MemberPlan[]>([]);
  protected readonly occurrences = signal<GymBlockOccurrence[]>([]);
  protected readonly reservations = signal<Reservation[]>([]);

  protected readonly isRasterLogo = computed(() => (this.gym()?.logoSvg ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.gym()?.logoSvg;
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });

  protected readonly planName = computed(() => this.membership()?.planName ?? 'Sin plan contratado');
  protected readonly quotaLabel = computed(() => {
    const m = this.membership();
    if (!m || !m.planId) return null;
    if (m.monthlyClasses === null) return 'Clases ilimitadas';
    return `${m.sessionsRemaining ?? 0} de ${m.monthlyClasses} clases restantes este mes`;
  });

  constructor() {
    this.demoPreviewService
      .getGym()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((gym) => this.gym.set(gym));

    this.demoPreviewService
      .getMembership()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((member) => this.membership.set(member));

    this.demoPreviewService
      .getPlans()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((plans) => this.plans.set(plans));

    const from = todayIsoInGymZone();
    const to = addDaysIso(from, 7);
    this.demoPreviewService
      .listOccurrences(from, to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((occurrences) => this.occurrences.set(occurrences));

    this.demoPreviewService
      .listReservations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((reservations) => {
        this.reservations.set(reservations);
        this.loaded.set(true);
      });
  }

  protected formatDate(iso: string): string {
    return formatShortDate(iso);
  }

  protected formatTime(time: string): string {
    return shortTime(time);
  }

  protected statusLabel(status: MembershipStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'Activo';
      case 'EXPIRING_SOON':
        return 'Por vencer';
      case 'EXPIRED':
        return 'Vencido';
      default:
        return 'Sin pago';
    }
  }

  protected statusColor(status: MembershipStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'EXPIRING_SOON':
        return 'warning';
      case 'EXPIRED':
        return 'danger';
      default:
        return 'medium';
    }
  }

  protected backToAdmin(): void {
    this.router.navigate(['/gym-admin']);
  }
}
