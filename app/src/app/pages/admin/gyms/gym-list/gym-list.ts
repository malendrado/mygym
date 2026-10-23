import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonFab,
  IonFabButton,
  IonHeader,
  IonIcon,
  IonSearchbar,
  IonText,
  IonTitle,
  IonToolbar,
  ViewWillEnter,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  addOutline,
  barbellOutline,
  businessOutline,
  checkmarkCircleOutline,
  closeCircleOutline,
  storefrontOutline,
  documentTextOutline,
  eyeOutline,
  logOutOutline,
  notificationsOutline,
  peopleOutline,
  pricetagOutline,
  sparklesOutline,
} from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { AnalyticsSummary, GymStats, GymSummary } from '../../../../core/models/gym.model';
import { PagedListState } from '../../../../core/state/paged-list.state';
import { GymLoader } from '../../../../core/components/gym-loader/gym-loader';
import { PaginationBar } from '../../../../core/components/pagination-bar/pagination-bar';
import { AuthService } from '../../../../core/services/auth.service';
import { toLogoImgSrc } from '../../../../core/utils/logo-src';

addIcons({
  'business-outline': businessOutline,
  'storefront-outline': storefrontOutline,
  'barbell-outline': barbellOutline,
  add: addOutline,
  'log-out-outline': logOutOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'close-circle-outline': closeCircleOutline,
  'notifications-outline': notificationsOutline,
  'people-outline': peopleOutline,
  'pricetag-outline': pricetagOutline,
  'sparkles-outline': sparklesOutline,
  'eye-outline': eyeOutline,
  'document-text-outline': documentTextOutline,
});

type Status = 'idle' | 'loading' | 'loaded' | 'error';
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

@Component({
  selector: 'app-gym-list',
  imports: [
    GymLoader,
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
    IonSearchbar,
    IonText,
    PaginationBar,
  ],
  templateUrl: './gym-list.html',
  styleUrl: './gym-list.scss',
})
export class GymList implements ViewWillEnter {
  protected readonly skeletonItems = [0, 1, 2, 3];
  protected readonly visitSkeletonRows = [0, 1, 2];
  private readonly gymService = inject(GymService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // Lista paginada (12 por página) con búsqueda por nombre/slug en el servidor — la lista crece con
  // cada cliente nuevo y cada tarjeta arrastra el logo SVG. Las calugas leen los conteos de TODOS
  // los gimnasios (stats), no cuentan las tarjetas de la página visible.
  // El estado también lo filtra el servidor (parámetro `active`): un filtro en cliente solo vería la página cargada.
  protected readonly statusFilter = signal<StatusFilter>('ALL');
  protected readonly loaderTips = ['Calentando motores…', 'Cargando las pesas…', 'Una serie más y listo…'] as const;

  protected readonly gymList = new PagedListState<GymSummary>(
    (query) =>
      this.gymService.listPage({
        ...query,
        active: this.statusFilter() === 'ALL' ? undefined : this.statusFilter() === 'ACTIVE',
      }),
    12,
  );
  protected readonly gyms = this.gymList.items;
  protected readonly stats = signal<GymStats | null>(null);

  // Panel de Visitas — contador propio (no depende de leer Vercel Analytics
  // por API, que no existe). Se carga aparte de la lista de gimnasios y
  // nunca bloquea la pantalla si falla (best-effort, mismo criterio que el
  // resto de las llamadas públicas de este proyecto).
  protected readonly analyticsStatus = signal<Status>('idle');
  protected readonly analytics = signal<AnalyticsSummary | null>(null);

  protected readonly statsStatus = signal<Status>('idle');

  // Sin datos todavía (cargando o con error) las calugas muestran "—": un 0 inventado se lee como dato real.
  protected readonly totalGyms = computed(() => this.stats()?.total ?? '—');
  protected readonly activeCount = computed(() => this.stats()?.active ?? '—');
  protected readonly inactiveCount = computed(() => {
    const stats = this.stats();
    return stats ? Math.max(0, stats.total - stats.active) : '—';
  });
  protected readonly statusFilterLabel = computed(() => {
    switch (this.statusFilter()) {
      case 'ACTIVE':
        return 'Activos';
      case 'INACTIVE':
        return 'Inactivos';
      default:
        return null;
    }
  });
  protected readonly totalCapacity = computed(() => this.stats()?.totalCapacity ?? '—');
  protected readonly brandedCount = computed(() => this.stats()?.branded ?? '—');

  // Panel de Visitas: solo las 10 primeras filas hasta que se pida ver todas.
  protected readonly visitsPreview = 10;
  protected readonly showAllVisits = signal(false);
  protected readonly visibleByGym = computed(() => {
    const rows = this.analytics()?.byGym ?? [];
    return this.showAllVisits() ? rows : rows.slice(0, this.visitsPreview);
  });

  /** Para dibujar cada fila de "Visitas" como una barra proporcional al máximo, no una
   *  lista plana de números — el mínimo de 1 evita dividir por cero cuando no hay datos. */
  protected readonly maxByGymVisits = computed(() =>
    Math.max(1, ...this.analytics()?.byGym.map((row) => row.total) ?? [1]),
  );

  protected readonly adminFirstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'admin');

  // Ionic cachea las páginas (provideIonicAngular usa IonicRouteStrategy por defecto, para las
  // animaciones de transición) — volver acá desde gym-form reusa esta MISMA instancia en vez de
  // recrearla, así que cargar los datos solo en el constructor los deja pegados para siempre
  // (ej. un gym recién desvinculado seguía apareciendo hasta un F5 manual, bug real reportado
  // por el usuario 2026-09-23). ionViewWillEnter sí dispara cada vez que la página se vuelve a
  // mostrar, esté cacheada o no — reemplaza al constructor como punto de carga.
  ionViewWillEnter(): void {
    this.gymList.reload();
    this.loadStats();
    this.loadAnalytics();
  }

  // Un super-admin no pertenece a ningún gimnasio puntual — al salir vuelve a
  // la landing principal de mygym, no a un /login genérico ni a un /j/:slug
  // que no le corresponde.
  protected async logout(): Promise<void> {
    await this.authService.logout();
    this.router.navigate(['/']);
  }

  protected logoSrc(gym: GymSummary): string | null {
    return toLogoImgSrc(gym.logoSvg);
  }

  protected onGymPage(page: number): void {
    this.gymList.goTo(page);
    document.querySelector('.gyms-section-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Elige el estado de la lista; tocar la caluga ya elegida (o "Gimnasios totales") quita el filtro. */
  protected setStatusFilter(filter: StatusFilter): void {
    this.statusFilter.set(this.statusFilter() === filter ? 'ALL' : filter);
    this.gymList.goTo(0);
  }

  protected clearStatusFilter(): void {
    this.statusFilter.set('ALL');
    this.gymList.goTo(0);
  }

  protected loadStats(): void {
    this.statsStatus.set('loading');
    this.gymService.stats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.statsStatus.set('loaded');
      },
      // Las calugas son informativas: si fallan, la lista sigue funcionando y se ofrece reintentar.
      error: () => this.statsStatus.set('error'),
    });
  }

  protected toggleAllVisits(): void {
    this.showAllVisits.update((v) => !v);
  }

  protected loadAnalytics(): void {
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
