import { Component, inject } from '@angular/core';
import { IonButton, IonIcon, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { notificationsOutline } from 'ionicons/icons';
import { PushService } from '../../services/push.service';

addIcons({ 'notifications-outline': notificationsOutline });

/**
 * Campana en el encabezado, visible solo cuando los avisos YA están activos en este teléfono: sirve para desactivarlos
 * sin tener que ir a los ajustes del sistema (activar se hace desde la franja app-push-prompt).
 */
@Component({
  selector: 'app-push-bell',
  imports: [IonButton, IonIcon],
  template: `
    @if (push.state() === 'enabled') {
      <ion-button
        fill="clear"
        class="push-bell"
        data-tip="Avisos activados · tocar para desactivar"
        aria-label="Avisos en este teléfono"
        aria-pressed="true"
        (click)="turnOff()"
      >
        <ion-icon name="notifications-outline" slot="icon-only" aria-hidden="true"></ion-icon>
      </ion-button>
    }
  `,
  styles: `
    .push-bell {
      height: 44px;
      width: 44px;
      margin: 0;
      --padding-start: 0;
      --padding-end: 0;
    }
  `,
})
export class PushBell {
  protected readonly push = inject(PushService);
  private readonly toastController = inject(ToastController);

  constructor() {
    void this.push.refresh();
  }

  protected async turnOff(): Promise<void> {
    await this.push.disable();
    // Lo apagó a propósito: la franja de "activar" no vuelve a aparecer enseguida.
    this.push.dismiss();
    const toast = await this.toastController.create({
      message: 'Avisos desactivados en este teléfono.',
      duration: 3000,
      position: 'bottom',
    });
    await toast.present();
  }
}
