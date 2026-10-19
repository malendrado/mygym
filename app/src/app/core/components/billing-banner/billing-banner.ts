import { Component, EventEmitter, OnInit, Output, computed, inject } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { alertCircleOutline, warningOutline } from 'ionicons/icons';
import { SubscriptionService } from '../../services/subscription.service';
import { formatIsoDate } from '../gym-subscription/gym-subscription';

addIcons({ 'warning-outline': warningOutline, 'alert-circle-outline': alertCircleOutline });

interface BannerView {
  tone: 'warn' | 'danger';
  title: string;
  detail: string;
}

/**
 * Aviso de vencimiento del plan mygym, arriba del panel del administrador y visible desde cualquier pestaña. Aparece solo en la
 * ventana de renovación (los últimos 3 días, el día del vencimiento y la gracia) y mientras no haya una transferencia en
 * revisión. No se puede cerrar: desaparece cuando el plan queda al día. El estado va en texto, no solo en color.
 */
@Component({
  selector: 'app-billing-banner',
  imports: [IonIcon],
  templateUrl: './billing-banner.html',
  styleUrl: './billing-banner.scss',
})
export class BillingBanner implements OnInit {
  private readonly service = inject(SubscriptionService);

  /** El administrador toca "Renovar ahora": el padre lo lleva a la tarjeta de pago. */
  @Output() renew = new EventEmitter<void>();

  protected readonly view = computed<BannerView | null>(() => {
    const s = this.service.mine();
    if (!s || !s.canRenew || s.transferPending || s.status === 'EXEMPT' || s.status === 'NOT_STARTED' || s.status === 'SUSPENDED') {
      return null;
    }
    const pauseOn = formatIsoDate(s.suspensionDate);
    if (s.status === 'GRACE') {
      return {
        tone: 'danger',
        title: 'Tu plan mygym está vencido',
        detail: `Renuévalo ahora: tu gimnasio se pausa el ${pauseOn}.`,
      };
    }
    const days = s.daysToDue;
    if (days == null) {
      return null;
    }
    const title =
      days === 0 ? 'Tu plan mygym vence hoy' : `Tu plan mygym vence en ${days} ${days === 1 ? 'día' : 'días'}`;
    return { tone: 'warn', title, detail: `Si no lo renuevas, tu gimnasio se pausa el ${pauseOn}.` };
  });

  ngOnInit(): void {
    // Si la tarjeta de pago ya lo pidió, se reutiliza; si no (otra pestaña), se pide una vez.
    if (this.service.mine() === null) {
      this.service.getMine().subscribe({ error: () => undefined });
    }
  }
}
