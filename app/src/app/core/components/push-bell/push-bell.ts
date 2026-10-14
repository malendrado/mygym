import { Component, computed, inject } from '@angular/core';
import { AlertController, IonButton, IonIcon, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { notificationsOffOutline, notificationsOutline } from 'ionicons/icons';
import { PushService, pushFailureMessage } from '../../services/push.service';

addIcons({ 'notifications-outline': notificationsOutline, 'notifications-off-outline': notificationsOffOutline });

/**
 * Campana en el encabezado: el interruptor de los avisos de este teléfono. Siempre está a mano cuando se pueden usar
 * (activados o no), así nunca se pierde el camino de vuelta:
 * - Activados: tocarla pide confirmación antes de desactivar (un toque accidental no debe apagarlos).
 * - Desactivados: tocarla los activa de inmediato (es una acción que suma, no necesita confirmación).
 * - Bloqueados en el teléfono: explica cómo desbloquearlos (el sitio no puede volver a pedir el permiso).
 */
@Component({
  selector: 'app-push-bell',
  imports: [IonButton, IonIcon],
  template: `
    @if (visible()) {
      <ion-button
        fill="clear"
        class="push-bell"
        [attr.data-tip]="enabled() ? 'Avisos activados · tocar para desactivar' : 'Avisos desactivados · tocar para activar'"
        aria-label="Avisos en este teléfono"
        [attr.aria-pressed]="enabled()"
        [disabled]="push.busy()"
        (click)="toggle()"
      >
        <ion-icon [name]="enabled() ? 'notifications-outline' : 'notifications-off-outline'" slot="icon-only" aria-hidden="true"></ion-icon>
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
  private readonly alertController = inject(AlertController);

  protected readonly enabled = computed(() => this.push.state() === 'enabled');
  // 'needs-install' (iPhone sin instalar) y 'unsupported' no tienen campana: ahí no hay nada que activar todavía.
  protected readonly visible = computed(() => {
    const state = this.push.state();
    return state === 'enabled' || ((state === 'default' || state === 'denied') && this.push.canOffer());
  });

  constructor() {
    void this.push.refresh();
  }

  protected async toggle(): Promise<void> {
    const state = this.push.state();
    if (state === 'enabled') {
      await this.confirmTurnOff();
    } else if (state === 'denied') {
      await this.toast('Los avisos están bloqueados para mygym en este teléfono. Actívalos en Ajustes → Notificaciones.', 'warning');
    } else {
      await this.turnOn();
    }
  }

  private async confirmTurnOff(): Promise<void> {
    const alert = await this.alertController.create({
      header: '¿Desactivar los avisos?',
      message: 'Dejarás de recibir avisos en este teléfono. Puedes volver a activarlos cuando quieras con esta misma campana.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Desactivar',
          role: 'destructive',
          handler: () => {
            void this.turnOff();
          },
        },
      ],
    });
    await alert.present();
  }

  private async turnOff(): Promise<void> {
    await this.push.disable();
    // Lo apagó a propósito: la franja de "activar" no vuelve a insistir; el camino de vuelta es esta campana.
    this.push.dismiss();
    await this.toast('Avisos desactivados en este teléfono. Puedes volver a activarlos con la campana.', 'medium');
  }

  private async turnOn(): Promise<void> {
    const result = await this.push.enable();
    if (result.status === 'ok') {
      await this.toast('Avisos activados. En unos segundos te llega un aviso de prueba.', 'success');
    } else if (result.status === 'denied') {
      await this.toast('No diste el permiso. Puedes activarlo en los ajustes de notificaciones del teléfono.', 'warning');
    } else {
      await this.toast(pushFailureMessage(result.reason, result.detail), 'danger');
    }
  }

  private async toast(message: string, color: string): Promise<void> {
    const toast = await this.toastController.create({
      message,
      duration: color === 'danger' ? 5000 : 4000,
      position: 'bottom',
      color,
    });
    await toast.present();
  }
}
