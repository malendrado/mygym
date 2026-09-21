import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonFab,
  IonFabButton,
  IonHeader,
  IonIcon,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  addOutline,
  barbellOutline,
  businessOutline,
  checkmarkCircleOutline,
  eyeOutline,
  logOutOutline,
  peopleOutline,
  sparklesOutline,
} from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { AnalyticsSummary, Gym } from '../../../../core/models/gym.model';
import { AuthService } from '../../../../core/services/auth.service';

addIcons({
  'business-outline': businessOutline,
  'barbell-outline': barbellOutline,
  add: addOutline,
  'log-out-outline': logOutOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'people-outline': peopleOutline,
  'sparkles-outline': sparklesOutline,
  'eye-outline': eyeOutline,
});

type Status = 'idle' | 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-gym-list',
  imports: [
    RouterLink,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonBadge,
    IonFab,
    IonFabButton,
    IonText,
  ],
  templateUrl: './gym-list.html',
  styleUrl: './gym-list.scss',
})
export class GymList {
  private readonly gymService = inject(GymService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly status = signal<Status>('idle');
  protected readonly gyms = signal<Gym[]>([]);

  // Panel de Visitas — contador propio (no depende de leer Vercel Analytics
  // por API, que no existe). Se carga aparte de la lista de gimnasios y
  // nunca bloquea la pantalla si falla (best-effort, mismo criterio que el
  // resto de las llamadas públicas de este proyecto).
  protected readonly analyticsStatus = signal<Status>('idle');
  protected readonly analytics = signal<AnalyticsSummary | null>(null);

  protected readonly activeCount = computed(() => this.gyms().filter((g) => g.active).length);
  protected readonly totalCapacity = computed(() => this.gyms().reduce((sum, g) => sum + g.maxUsers, 0));
  protected readonly brandedCount = computed(() => this.gyms().filter((g) => !!g.logoSvg).length);

  protected readonly adminFirstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'admin');

  constructor() {
    this.load();
    this.loadAnalytics();
  }

  // Un super-admin no pertenece a ningún gimnasio puntual — al salir vuelve a
  // la landing principal de mygym, no a un /login genérico ni a un /j/:slug
  // que no le corresponde.
  protected async logout(): Promise<void> {
    await this.authService.logout();
    this.router.navigate(['/']);
  }

  protected isRasterLogo(gym: Gym): boolean {
    return (gym.logoSvg ?? '').startsWith('data:image');
  }

  protected safeLogo(gym: Gym): SafeHtml | null {
    return gym.logoSvg && !this.isRasterLogo(gym) ? this.sanitizer.bypassSecurityTrustHtml(gym.logoSvg) : null;
  }

  private load(): void {
    this.status.set('loading');
    this.gymService.list().subscribe({
      next: (gyms) => {
        this.gyms.set(gyms);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  private loadAnalytics(): void {
    this.analyticsStatus.set('loading');
    this.gymService.getAnalyticsSummary().subscribe({
      next: (summary) => {
        this.analytics.set(summary);
        this.analyticsStatus.set('loaded');
      },
      error: () => this.analyticsStatus.set('error'),
    });
  }
}
