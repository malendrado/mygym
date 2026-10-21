import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, computed, inject, signal } from '@angular/core';
import {
  IonButton,
  IonCheckbox,
  IonIcon,
  IonInput,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { alertCircleOutline, documentTextOutline, mailOutline } from 'ionicons/icons';
import { BillingKind, ContractView, SignerCapacity } from '../../models/subscription.model';
import { SubscriptionService } from '../../services/subscription.service';
import { errorText } from '../../utils/error-text';
import { formatRut, isValidRut } from '../../utils/rut';

addIcons({ 'alert-circle-outline': alertCircleOutline, 'document-text-outline': documentTextOutline, 'mail-outline': mailOutline });

/** Segundos que hay que esperar para pedir otro código (el servidor aplica el mismo mínimo). */
const RESEND_SECONDS = 60;

/**
 * Firma del contrato de servicios por el administrador del gimnasio: lee el texto, declara quién es y en qué calidad firma, pide un
 * código que llega a su correo y lo ingresa. El servidor guarda la constancia; esta pieza solo guía el paso y avisa con `signed`.
 */
@Component({
  selector: 'app-contract-sign',
  imports: [IonButton, IonCheckbox, IonIcon, IonInput, IonSelect, IonSelectOption, IonSpinner],
  templateUrl: './contract-sign.html',
  styleUrl: './contract-sign.scss',
})
export class ContractSign implements OnInit, OnDestroy {
  private readonly service = inject(SubscriptionService);
  private readonly toastController = inject(ToastController);

  /** Tipo de factura del gimnasio: define qué calidades puede declarar quien firma. */
  @Input() kind: BillingKind = 'COMPANY';
  @Output() signed = new EventEmitter<void>();

  protected readonly view = signal<ContractView | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly busy = signal(false);

  protected readonly name = signal('');
  protected readonly rut = signal('');
  protected readonly capacity = signal<SignerCapacity>('LEGAL_REP');
  protected readonly adult = signal(false);
  protected readonly powers = signal(false);
  protected readonly codeSent = signal(false);
  protected readonly maskedEmail = signal('');
  protected readonly code = signal('');
  protected readonly cooldown = signal(0);

  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly capacities = computed<{ value: SignerCapacity; label: string }[]>(() =>
    this.kind === 'PERSON'
      ? [
          { value: 'PERSON_OWNER', label: 'Titular (persona natural)' },
          { value: 'ATTORNEY', label: 'Apoderado' },
        ]
      : [
          { value: 'LEGAL_REP', label: 'Representante legal' },
          { value: 'ATTORNEY', label: 'Apoderado' },
        ],
  );
  protected readonly rutInvalid = computed(() => this.rut().trim() !== '' && !isValidRut(this.rut()));
  protected readonly canSend = computed(
    () => !this.busy() && this.cooldown() === 0 && this.name().trim().length >= 3 && isValidRut(this.rut()) && this.adult() && this.powers(),
  );
  protected readonly canSign = computed(() => !this.busy() && /^\d{6}$/.test(this.code()));

  ngOnInit(): void {
    this.capacity.set(this.kind === 'PERSON' ? 'PERSON_OWNER' : 'LEGAL_REP');
    this.service.getContract().subscribe({
      next: (v) => {
        this.view.set(v);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  protected sendCode(): void {
    if (!this.canSend()) {
      return;
    }
    this.busy.set(true);
    this.service
      .sendContractCode({
        signerName: this.name().trim(),
        signerRut: this.rut().trim(),
        capacity: this.capacity(),
        adult: this.adult(),
        powers: this.powers(),
      })
      .subscribe({
        next: (r) => {
          this.busy.set(false);
          this.maskedEmail.set(r.maskedEmail);
          this.codeSent.set(true);
          this.code.set('');
          this.startCooldown();
          void this.toast(`Te enviamos un código a ${r.maskedEmail}. Vence en 10 minutos.`);
        },
        error: (err) => {
          this.busy.set(false);
          void this.toast(errorText(err, 'No pudimos enviar el código. Intenta nuevamente.'), 'danger');
        },
      });
  }

  protected sign(): void {
    if (!this.canSign()) {
      return;
    }
    this.busy.set(true);
    this.service.signContract(this.code()).subscribe({
      next: () => {
        this.busy.set(false);
        void this.toast('Contrato firmado. Te enviamos la constancia por correo.');
        this.signed.emit();
      },
      error: (err) => {
        this.busy.set(false);
        void this.toast(errorText(err, 'No pudimos firmar el contrato. Intenta nuevamente.'), 'danger');
      },
    });
  }

  protected onCode(event: Event): void {
    this.code.set(this.text(event).replace(/\D/g, ''));
  }

  protected onRutBlur(): void {
    this.rut.set(formatRut(this.rut()));
  }

  protected text(event: Event): string {
    return String((event as CustomEvent).detail?.value ?? '');
  }

  private startCooldown(): void {
    this.stopTimer();
    this.cooldown.set(RESEND_SECONDS);
    this.timer = setInterval(() => {
      this.cooldown.update((n) => Math.max(0, n - 1));
      if (this.cooldown() === 0) {
        this.stopTimer();
      }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async toast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4500, position: 'bottom', color });
    await toast.present();
  }
}
