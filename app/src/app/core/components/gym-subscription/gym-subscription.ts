import { NgTemplateOutlet } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AlertController,
  IonButton,
  IonIcon,
  IonInput,
  IonLabel,
  IonSegment,
  IonSegmentButton,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonToggle,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  cardOutline,
  checkmarkCircleOutline,
  copyOutline,
  receiptOutline,
  swapHorizontalOutline,
  timeOutline,
} from 'ionicons/icons';
import {
  BillingInfo,
  BillingKind,
  BillingStatus,
  GymSubscription,
  MygymPlan,
  PaymentMethod,
  PaymentStatus,
  SubscriptionPayment,
} from '../../models/subscription.model';
import { GymPauseService } from '../../services/gym-pause.service';
import { SubscriptionService } from '../../services/subscription.service';
import { isValidBillingEmail, normalizeBillingEmail } from '../../utils/billing-email';
import { cleanRut, formatRut, isValidRut } from '../../utils/rut';

addIcons({
  'card-outline': cardOutline,
  'swap-horizontal-outline': swapHorizontalOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'copy-outline': copyOutline,
  'alert-circle-outline': alertCircleOutline,
  'time-outline': timeOutline,
  'receipt-outline': receiptOutline,
});

type Tone = 'ok' | 'warn' | 'danger' | 'neutral';

interface StatusView {
  label: string;
  tone: Tone;
  hint: string;
}

const METHOD_LABEL: Record<PaymentMethod, string> = { FLOW: 'Flow', TRANSFER: 'Transferencia', MANUAL: 'Pago manual' };
const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Sin confirmar',
  REPORTED: 'En revisión',
  PAID: 'Pagado',
  REJECTED: 'Rechazado',
  VOIDED: 'Anulado',
};

const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** "2026-11-15" -> "15 de noviembre de 2026" (sin pasar por Date: no hay corrimiento de zona horaria). */
export function formatIsoDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return `${d} de ${MONTHS[m - 1]} de ${y}`;
}

/** "2026-10-20" -> "20 de octubre". */
function dayMonth(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [, m, d] = iso.slice(0, 10).split('-').map(Number);
  return `${d} de ${MONTHS[m - 1]}`;
}

function shortDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [, m, d] = iso.slice(0, 10).split('-').map(Number);
  return `${d} ${MONTHS[m - 1].slice(0, 3)}`;
}

export function formatClp(amount: number | null | undefined): string {
  return amount == null ? '—' : '$' + amount.toLocaleString('es-CL');
}

function santiagoToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Santiago' });
}

function toInt(value: unknown): number | null {
  const n = parseInt(String(value ?? '').trim(), 10);
  return Number.isFinite(n) && n > 0 ? n : null;
}

/**
 * Suscripción del gimnasio a mygym. Una sola pieza para dos miradas: el administrador del gimnasio (ve lo que paga, renueva con
 * Flow o avisa una transferencia) y el super-admin (activa el gimnasio, registra pagos, confirma transferencias, cambia el plan).
 * El estado nunca va solo por color: siempre hay un texto ("Al día", "Vence en 2 días", "En pausa por falta de pago").
 */
@Component({
  selector: 'app-gym-subscription',
  imports: [NgTemplateOutlet, IonButton, IonIcon, IonInput, IonLabel, IonSegment, IonSegmentButton, IonSelect, IonSelectOption, IonSpinner, IonToggle],
  templateUrl: './gym-subscription.html',
  styleUrl: './gym-subscription.scss',
})
export class GymSubscriptionCard implements OnInit, OnDestroy {
  private readonly service = inject(SubscriptionService);
  private readonly pause = inject(GymPauseService);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);

  /** `admin`: el administrador del gimnasio. `super`: el super-admin sobre el gimnasio que se le indique. */
  @Input() mode: 'admin' | 'super' = 'admin';
  /** Dentro del aviso de pausa: solo lo necesario para pagar. */
  @Input() overlay = false;
  @Input() set gymId(value: number | null) {
    this._gymId = value;
    if (this.mode === 'super' && value !== null) {
      this.load();
    }
  }
  /** El estado de la suscripción cambió (pago, activación...): el padre puede recargar cupos o datos del gimnasio. */
  @Output() changed = new EventEmitter<void>();

  private _gymId: number | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly sub = signal<GymSubscription | null>(null);
  protected readonly plans = signal<MygymPlan[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly busy = signal(false);
  protected readonly confirmingPayment = signal(false);

  // ---- Formularios (todos con signals; sin ReactiveForms para no sumar peso)
  protected readonly showTransfer = signal(false);
  protected readonly transferDate = signal(santiagoToday());
  protected readonly transferBank = signal('');
  protected readonly transferOperation = signal('');
  protected readonly transferError = signal<string | null>(null);

  protected readonly showBilling = signal(false);
  protected readonly bKind = signal<BillingKind>('COMPANY');
  protected readonly bRut = signal('');
  protected readonly bName = signal('');
  protected readonly bGiro = signal('');
  protected readonly bAddress = signal('');
  protected readonly bEmail = signal('');
  protected readonly billingError = signal<string | null>(null);
  /** Los errores por campo se muestran recién después del primer intento de guardar (no mientras se escribe). */
  protected readonly billingTried = signal(false);

  /** Mismos cinco datos para una empresa o una persona natural: solo cambian los títulos y las ayudas. */
  protected readonly billingLabels = computed(() =>
    this.bKind() === 'PERSON'
      ? {
          rut: 'RUT',
          name: 'Nombre completo',
          giro: 'Giro',
          giroHelp: 'La actividad de tu inicio de actividades en el SII.',
          address: 'Dirección',
          email: 'Correo para la factura',
        }
      : {
          rut: 'RUT de la empresa',
          name: 'Razón social',
          giro: 'Giro',
          giroHelp: 'El giro que figura en el SII.',
          address: 'Dirección comercial',
          email: 'Correo para la factura',
        },
  );

  /**
   * Error de cada campo. Quien administra el gimnasio debe completar los cinco; el super-admin puede guardar de a poco, pero lo
   * que escriba tiene que ser válido (RUT con dígito verificador, correo bien escrito). El servidor repite estas mismas reglas.
   */
  protected readonly billingErrors = computed(() => {
    const required = !this.isSuper();
    const person = this.bKind() === 'PERSON';
    const rut = this.bRut().trim();
    const email = this.bEmail().trim();
    return {
      rut: !rut ? (required ? 'Escribe el RUT.' : null) : isValidRut(rut) ? null : 'Revisa el RUT: el dígito verificador no coincide.',
      name: this.bName().trim() ? null : required ? (person ? 'Escribe tu nombre completo.' : 'Escribe la razón social.') : null,
      giro: this.bGiro().trim() ? null : required ? 'Escribe el giro.' : null,
      address: this.bAddress().trim() ? null : required ? 'Escribe la dirección.' : null,
      email: !email ? (required ? 'Escribe el correo.' : null) : isValidBillingEmail(email) ? null : 'Escribe un correo válido, como nombre@empresa.cl.',
    };
  });

  /** Aviso (no bloquea): los RUT de empresa parten desde 50.000.000; uno menor suele ser de una persona. */
  protected readonly rutKindHint = computed(() => {
    const rut = this.bRut().trim();
    if (this.bKind() !== 'COMPANY' || !rut || !isValidRut(rut)) {
      return null;
    }
    return parseInt(cleanRut(rut).slice(0, -1), 10) < 50_000_000 ? 'Este RUT parece de una persona natural. Si es tu caso, elige "Persona natural".' : null;
  });

  protected readonly activatePlanId = signal<number | null>(null);
  protected readonly activateStart = signal(santiagoToday());
  protected readonly activatePrice = signal('');
  protected readonly activateMaxUsers = signal('');
  protected readonly activateMethod = signal<PaymentMethod>('TRANSFER');
  protected readonly activateOperation = signal('');
  protected readonly activateNote = signal('');
  protected readonly activateError = signal<string | null>(null);

  protected readonly editPrice = signal('');
  protected readonly editMaxUsers = signal('');
  protected readonly editStart = signal('');
  protected readonly editExempt = signal(false);

  protected readonly newPlanId = signal<number | null>(null);
  protected readonly newPlanPrice = signal('');

  protected readonly payMethod = signal<PaymentMethod>('TRANSFER');
  protected readonly payOperation = signal('');
  protected readonly payNote = signal('');
  protected readonly showPay = signal(false);

  protected readonly isSuper = computed(() => this.mode === 'super');
  protected readonly activePlans = computed(() => this.plans().filter((p) => p.active));
  protected readonly selectedActivationPlan = computed(() => this.plans().find((p) => p.id === this.activatePlanId()) ?? null);
  protected readonly selectedNewPlan = computed(() => this.plans().find((p) => p.id === this.newPlanId()) ?? null);
  protected readonly started = computed(() => this.sub()?.anchorDate != null);
  /** El monto de la tarjeta ya es de otro plan (el cambio rige en la próxima renovación): se muestra "hoy / desde" en vez de mezclar los dos. */
  protected readonly changing = computed(() => {
    const s = this.sub();
    return !!s?.planName && !!s.chargePlanName && s.chargePlanName !== s.planName;
  });
  /** Lo que ya está guardado en el servidor (no lo que se está escribiendo): con eso el servidor deja o no pagar. */
  protected readonly billingComplete = computed(() => {
    const b = this.sub()?.billing;
    return !!b && [b.rut, b.name, b.giro, b.address, b.email].every((v) => !!v && v.trim() !== '');
  });
  /** Faltan datos y el administrador puede renovar ahora: el formulario va arriba, antes de los botones de pago. */
  protected readonly billingFirst = computed(() => {
    const s = this.sub();
    return !this.isSuper() && !!s && s.canRenew && s.status !== 'EXEMPT' && !s.transferPending && !this.billingComplete();
  });

  protected readonly statusView = computed<StatusView | null>(() => {
    const s = this.sub();
    return s ? this.buildStatus(s) : null;
  });

  /** Último pago confirmado: el único que se puede anular. */
  protected readonly lastPaidId = computed(() => this.sub()?.payments.find((p) => p.status === 'PAID')?.id ?? null);
  protected readonly reported = computed(() => this.sub()?.payments.filter((p) => p.status === 'REPORTED') ?? []);
  protected readonly flowDiffers = computed(() => {
    const s = this.sub();
    return s != null && s.flowTotalClp != null && s.totalClp != null && s.flowTotalClp !== s.totalClp;
  });
  protected readonly visibleHistory = computed(() => (this.sub()?.payments ?? []).filter((p) => p.status !== 'PENDING' || this.isSuper()));

  protected readonly clp = formatClp;
  protected readonly longDate = formatIsoDate;
  protected readonly dayMonth = dayMonth;
  protected readonly short = shortDate;
  protected readonly methodLabel = (m: PaymentMethod) => METHOD_LABEL[m];
  protected readonly paymentStatusLabel = (s: PaymentStatus) => PAYMENT_STATUS_LABEL[s];

  ngOnInit(): void {
    if (this.mode === 'admin') {
      this.load();
      this.handleFlowReturn();
    } else {
      this.service.listPlans().subscribe({
        next: (plans) => {
          this.plans.set(plans);
          if (this.activatePlanId() === null && !this.started()) {
            this.activatePlanId.set(this.activePlans()[0]?.id ?? null);
          }
        },
        error: () => undefined,
      });
    }
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
    }
  }

  protected reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(this.sub() === null);
    this.loadError.set(false);
    const request = this.mode === 'admin' ? this.service.getMine() : this.service.get(this._gymId!);
    request.subscribe({
      next: (s) => {
        this.apply(s);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  /** Deja la pantalla con lo que respondió el servidor y rellena los formularios con sus valores actuales. */
  private apply(s: GymSubscription): void {
    this.sub.set(s);
    this.bKind.set(s.billing.kind ?? 'COMPANY');
    this.bRut.set(s.billing.rut ?? '');
    this.bName.set(s.billing.name ?? '');
    this.bGiro.set(s.billing.giro ?? '');
    this.bAddress.set(s.billing.address ?? '');
    this.bEmail.set(s.billing.email ?? '');
    this.editPrice.set(s.priceOverrideClp?.toString() ?? '');
    this.editMaxUsers.set(s.maxUsersOverride?.toString() ?? '');
    this.editStart.set(s.anchorDate ?? '');
    this.editExempt.set(s.exempt);
    if (this.activatePlanId() === null && this.plans().length > 0 && !this.started()) {
      this.activatePlanId.set(this.activePlans()[0]?.id ?? null);
    }
  }

  /** Vuelta desde Flow: el aviso de pago llega al servidor aparte, así que se consulta unos segundos hasta que aparezca. */
  private handleFlowReturn(): void {
    if (this.mode !== 'admin' || !location.search.includes('subscription=return')) {
      return;
    }
    history.replaceState(null, '', location.pathname);
    this.confirmingPayment.set(true);
    const paidBefore = () => this.sub()?.payments.filter((p) => p.status === 'PAID').length ?? 0;
    let attempts = 0;
    let baseline: number | null = null;
    const poll = () => {
      attempts++;
      this.service.getMine().subscribe({
        next: (s) => {
          baseline ??= paidBefore();
          const now = s.payments.filter((p) => p.status === 'PAID').length;
          this.apply(s);
          this.loading.set(false);
          if (now > baseline) {
            this.confirmingPayment.set(false);
            void this.toast('¡Pago confirmado! Gracias.');
            this.afterPayment();
            return;
          }
          if (attempts >= 8) {
            this.confirmingPayment.set(false);
            void this.toast('Seguimos confirmando tu pago con Flow. Se verá reflejado en unos minutos.', 'medium');
            return;
          }
          this.pollTimer = setTimeout(poll, 3000);
        },
        error: () => {
          this.confirmingPayment.set(false);
        },
      });
    };
    // El primer estado se lee antes de contar, para no confundir un pago anterior con el nuevo.
    this.service.getMine().subscribe({
      next: (s) => {
        this.apply(s);
        this.loading.set(false);
        baseline = s.payments.filter((p) => p.status === 'PAID').length;
        poll();
      },
      error: () => this.confirmingPayment.set(false),
    });
  }

  // ---- Administrador del gimnasio

  protected payWithFlow(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.service.checkout().subscribe({
      next: ({ url }) => {
        window.location.href = url;
      },
      error: (err: Error) => {
        this.busy.set(false);
        void this.toast(err.message || 'No pudimos iniciar el pago. Intenta nuevamente.', 'danger');
      },
    });
  }

  /** Todos los datos de la cuenta, una línea por dato: varias apps de banco los leen del portapapeles al agregar un destinatario. */
  protected copyAllBank(bank: GymSubscription['bank']): Promise<void> {
    const lines = [
      ['Nombre', bank.holder],
      ['RUT', bank.rut],
      ['Banco', bank.bank],
      ['Tipo de cuenta', bank.accountType],
      ['Número de cuenta', bank.accountNumber],
      ['Correo', bank.email],
    ]
      .filter(([, value]) => !!value)
      .map(([label, value]) => `${label}: ${value}`);
    return this.copy(lines.join('\n'));
  }

  protected async copy(value: string | null | undefined): Promise<void> {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      const toast = await this.toastController.create({ message: 'Copiado', duration: 1500, position: 'bottom', color: 'success' });
      await toast.present();
    } catch {
      const toast = await this.toastController.create({ message: 'No pudimos copiar. Cópialo a mano.', duration: 2500, position: 'bottom', color: 'danger' });
      await toast.present();
    }
  }

  protected submitTransfer(): void {
    const bank = this.transferBank().trim();
    if (!this.transferDate() || !bank) {
      this.transferError.set('Indica la fecha y el banco desde el que transferiste.');
      return;
    }
    this.transferError.set(null);
    this.run(
      this.service.reportTransfer({
        transferDate: this.transferDate(),
        bank,
        operation: this.transferOperation().trim() || null,
      }),
      'Listo, le avisamos a mygym. Apenas confirme tu transferencia se reactiva todo.',
      () => this.showTransfer.set(false),
    );
  }

  /** Al salir del campo RUT lo deja como 12.345.678-5 (si es válido; si no, lo deja como está y muestra el error al guardar). */
  protected tidyRut(): void {
    this.bRut.set(formatRut(this.bRut().trim()));
  }

  protected tidyEmail(): void {
    this.bEmail.set(normalizeBillingEmail(this.bEmail()));
  }

  protected saveBilling(): void {
    this.billingTried.set(true);
    if (Object.values(this.billingErrors()).some((error) => error !== null)) {
      this.billingError.set('Revisa los datos marcados.');
      return;
    }
    this.billingError.set(null);
    const rut = this.bRut().trim();
    const email = this.bEmail().trim();
    const info: BillingInfo = {
      kind: this.bKind(),
      rut: rut ? formatRut(rut) : null,
      name: this.bName().trim() || null,
      giro: this.bGiro().trim() || null,
      address: this.bAddress().trim() || null,
      email: email ? normalizeBillingEmail(email) : null,
    };
    const request =
      this.mode === 'admin' ? this.service.updateBillingMine(info) : this.service.updateBilling(this._gymId!, info);
    this.run(request, 'Datos de factura guardados.', () => this.showBilling.set(false), false);
  }

  // ---- Super-admin

  protected activate(): void {
    const plan = this.selectedActivationPlan();
    if (!plan) {
      this.activateError.set('Elige un plan.');
      return;
    }
    const price = toInt(this.activatePrice());
    if (plan.priceClp == null && price == null) {
      this.activateError.set('Este plan es a medida: indica el precio mensual neto de este gimnasio.');
      return;
    }
    this.activateError.set(null);
    this.run(
      this.service.activate(this._gymId!, {
        planId: plan.id,
        startDate: this.activateStart() || null,
        priceOverrideClp: price,
        maxUsersOverride: toInt(this.activateMaxUsers()),
        method: this.activateMethod(),
        operation: this.activateOperation().trim() || null,
        note: this.activateNote().trim() || null,
      }),
      'Gimnasio activado. Ya puede usar mygym.',
      undefined,
      true,
    );
  }

  protected saveSettings(): void {
    this.run(
      this.service.update(this._gymId!, {
        priceOverrideClp: toInt(this.editPrice()),
        maxUsersOverride: toInt(this.editMaxUsers()),
        anchorDate: this.editStart() || null,
        exempt: this.editExempt(),
      }),
      'Suscripción guardada.',
      undefined,
      true,
    );
  }

  protected changePlan(): void {
    const plan = this.selectedNewPlan();
    if (!plan) {
      return;
    }
    const price = toInt(this.newPlanPrice());
    if (plan.priceClp == null && price == null) {
      void this.toast('Este plan es a medida: indica el precio mensual neto.', 'danger');
      return;
    }
    this.run(
      this.service.changePlan(this._gymId!, plan.id, price),
      this.started() ? 'Plan cambiado. Rige desde el próximo período.' : 'Plan asignado. El gimnasio se activa cuando pague.',
      () => this.newPlanId.set(null),
      true,
    );
  }

  protected markPaid(): void {
    this.run(
      this.service.markPaid(this._gymId!, this.payMethod(), this.payOperation().trim() || null, this.payNote().trim() || null),
      'Pago registrado.',
      () => {
        this.showPay.set(false);
        this.payOperation.set('');
        this.payNote.set('');
      },
      true,
    );
  }

  protected confirmTransfer(payment: SubscriptionPayment): void {
    this.run(this.service.confirmTransfer(this._gymId!, payment.id), 'Transferencia confirmada. El gimnasio quedó al día.', undefined, true);
  }

  protected async rejectTransfer(payment: SubscriptionPayment): Promise<void> {
    const alert = await this.alertController.create({
      header: 'Rechazar transferencia',
      message: 'El administrador recibirá un aviso. Puedes indicar el motivo.',
      inputs: [{ name: 'reason', type: 'text', placeholder: 'Motivo (opcional)' }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Rechazar',
          role: 'destructive',
          handler: (data: { reason?: string }) => {
            this.run(this.service.rejectTransfer(this._gymId!, payment.id, data.reason?.trim() || null), 'Transferencia rechazada.', undefined, true);
          },
        },
      ],
    });
    await alert.present();
  }

  protected async voidPayment(payment: SubscriptionPayment): Promise<void> {
    const alert = await this.alertController.create({
      header: '¿Anular este pago?',
      message: 'La suscripción vuelve exactamente a como estaba antes de este pago.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Anular pago',
          role: 'destructive',
          handler: () => this.run(this.service.voidPayment(this._gymId!, payment.id), 'Pago anulado.', undefined, true),
        },
      ],
    });
    await alert.present();
  }

  // ---- Internos

  private run(
    request: Observable<GymSubscription>,
    successMessage: string,
    after?: () => void,
    notifyParent = false,
  ): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    request.subscribe({
      next: (s) => {
        this.busy.set(false);
        this.apply(s);
        after?.();
        void this.toast(successMessage);
        if (notifyParent) {
          this.changed.emit();
        }
        if (this.mode === 'admin') {
          this.afterPayment();
        }
      },
      error: (err: Error) => {
        this.busy.set(false);
        void this.toast(err.message || 'No pudimos completar la acción. Intenta nuevamente.', 'danger');
      },
    });
  }

  /** Si el gimnasio estaba en pausa por no pago y ya quedó al día, la pantalla se recarga sola al consultar el estado. */
  private afterPayment(): void {
    void this.pause.check();
    this.changed.emit();
  }

  private buildStatus(s: GymSubscription): StatusView {
    const forAdmin = this.mode === 'admin';
    const due = formatIsoDate(s.dueDate);
    const status: BillingStatus = s.status;
    switch (status) {
      case 'EXEMPT':
        return { label: 'Sin cobro', tone: 'neutral', hint: forAdmin ? 'Tu gimnasio no tiene cobro de mygym.' : 'Este gimnasio está exento de cobro.' };
      case 'NOT_STARTED':
        return {
          label: 'Sin activar',
          tone: 'warn',
          hint: forAdmin
            ? 'Tu cuenta todavía no está activada. Se activa cuando mygym recibe tu primer pago.'
            : 'En pausa hasta recibir el primer pago. Actívalo cuando llegue.',
        };
      case 'GRACE':
        return {
          label: 'Plan vencido',
          tone: 'danger',
          hint: `Venció el ${due}. Se pausa el ${formatIsoDate(s.suspensionDate)} si no se renueva.`,
        };
      case 'SUSPENDED':
        return {
          label: 'En pausa por falta de pago',
          tone: 'danger',
          hint: forAdmin ? `Venció el ${due}. Con Flow se reactiva al instante; con transferencia, cuando la confirmemos.` : `Venció el ${due}. Se reactiva al registrar el pago.`,
        };
      default: {
        const days = s.daysToDue;
        if (days === 0) {
          return { label: 'Vence hoy', tone: 'warn', hint: `Renueva hoy para evitar la pausa.` };
        }
        if (days != null && days <= 3) {
          return { label: `Vence en ${days} ${days === 1 ? 'día' : 'días'}`, tone: 'warn', hint: `Próximo pago: ${due}.` };
        }
        return { label: 'Al día', tone: 'ok', hint: `Próximo pago: ${due}.` };
      }
    }
  }

  private async toast(message: string, color: 'success' | 'danger' | 'medium' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4500, position: 'bottom', color });
    await toast.present();
  }

  // Binding helpers: evitan repetir el cast en la plantilla.
  protected text(event: Event): string {
    return String((event as CustomEvent).detail?.value ?? '');
  }
}
