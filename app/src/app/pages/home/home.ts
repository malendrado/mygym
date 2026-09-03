import { Component, inject, signal } from '@angular/core';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonCard,
  IonCardHeader,
  IonCardTitle,
  IonCardSubtitle,
  IonCardContent,
  IonButton,
  IonList,
  IonItem,
  IonLabel,
  IonBadge,
} from '@ionic/angular';
import { HealthService } from '../../core/services/health.service';

type ApiState = 'idle' | 'loading' | 'up' | 'down';

@Component({
  selector: 'app-home',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardSubtitle,
    IonCardContent,
    IonButton,
    IonList,
    IonItem,
    IonLabel,
    IonBadge,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly healthService = inject(HealthService);

  protected readonly apiState = signal<ApiState>('idle');

  protected readonly nextSteps = [
    'Elegir la base de datos del backend',
    'Modelar el dominio de negocio (controllers → services → repositories → models)',
    'Definir autenticación y autorización',
    'Agregar las plataformas nativas con `npx cap add android` / `npx cap add ios`',
  ];

  protected checkApiHealth(): void {
    this.apiState.set('loading');
    this.healthService.check().subscribe({
      next: (result) => this.apiState.set(result.status === 'UP' ? 'up' : 'down'),
      error: () => this.apiState.set('down'),
    });
  }
}
