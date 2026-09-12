import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonContent,
  IonFab,
  IonFabButton,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { addOutline, barbellOutline, businessOutline } from 'ionicons/icons';
import { GymService } from '../../../../core/services/gym.service';
import { Gym } from '../../../../core/models/gym.model';
import { AuthService } from '../../../../core/services/auth.service';

addIcons({
  'business-outline': businessOutline,
  'barbell-outline': barbellOutline,
  add: addOutline,
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
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonIcon,
    IonList,
    IonItem,
    IonLabel,
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

  protected readonly status = signal<Status>('idle');
  protected readonly gyms = signal<Gym[]>([]);

  constructor() {
    this.load();
  }

  protected logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
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
}
