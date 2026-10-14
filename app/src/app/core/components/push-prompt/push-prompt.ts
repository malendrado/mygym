import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { IonButton, IonIcon, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeOutline, notificationsOutline } from 'ionicons/icons';
import { PushService, pushFailureMessage } from '../../services/push.service';

addIcons({ 'close-outline': closeOutline, 'notifications-outline': notificationsOutline });

type Audience = 'member' | 'staff';

/**
 * Franja fina para activar los avisos push en este teléfono. Solo aparece cuando se puede y todavía no está activo
 * (ver PushService.canOffer), se puede cerrar con "Ahora no" (14 días) y NUNCA pide el permiso por sí sola: primero
 * explica, el permiso del teléfono lo dispara el botón.
 */
@Component({
  selector: 'app-push-prompt',
  imports: [IonButton, IonIcon],
  templateUrl: './push-prompt.html',
  styleUrl: './push-prompt.scss',
})
export class PushPrompt implements OnInit {
  @Input() audience: Audience = 'member';

  protected readonly push = inject(PushService);
  private readonly toastController = inject(ToastController);

  protected readonly busy = signal(false);
  protected readonly visible = computed(() => this.push.canOffer() && !this.push.dismissed());

  ngOnInit(): void {
    void this.push.refresh();
    // Si ya estaban activos en este teléfono (otra persona usó antes la sesión), pasan a pertenecer a quien entró ahora.
    void this.push.syncOwnership();
  }

  protected get message(): string {
    return this.audience === 'staff'
      ? 'Entérate al instante cuando un socio cancele una clase.'
      : 'Te avisamos si se libera un cupo, 1 hora antes de tu clase y si el gimnasio cierra.';
  }

  protected async activate(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    try {
      const result = await this.push.enable();
      if (result.status === 'ok') {
        await this.toast('Listo, en unos segundos te llega un aviso de prueba.', 'success');
      } else if (result.status === 'denied') {
        await this.toast('No diste el permiso. Puedes activarlo en los ajustes de notificaciones del teléfono.', 'warning');
      } else {
        await this.toast(pushFailureMessage(result.reason, result.detail), 'danger');
      }
    } finally {
      this.busy.set(false);
    }
  }

  private async toast(message: string, color: string): Promise<void> {
    const toast = await this.toastController.create({ message, duration: color === 'danger' ? 5000 : 4000, position: 'bottom', color });
    await toast.present();
  }
}
