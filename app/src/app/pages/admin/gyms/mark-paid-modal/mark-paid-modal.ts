import { Component, DestroyRef, EventEmitter, Input, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonSelect,
  IonSelectOption,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { cardOutline, checkmarkCircle, timeOutline } from 'ionicons/icons';
import { CHILE_BANKS, GymPlan } from '../../../../core/models/gym.model';
import { PaymentPreview } from '../../../../core/models/member.model';
import { santiagoNow } from '../../../../core/utils/attendance';
import { errorText } from '../../../../core/utils/error-text';
import { formatIsoDate } from '../../../../core/utils/date-format';
import { minStartDate, periodEndOf, periodText, remainingText, startDateShortcuts } from '../../../../core/utils/payment-dates';

type PaymentMethod = 'transfer' | 'cash' | 'other';
const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'transfer', label: 'Transferencia' },
  { value: 'cash', label: 'Efectivo' },
  { value: 'other', label: 'Otro' },
];

addIcons({ 'card-outline': cardOutline, 'checkmark-circle': checkmarkCircle, 'time-outline': timeOutline });

@Component({
  selector: 'app-mark-paid-modal',
  imports: [
    IonHeader,
    IonFooter,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonItem,
    IonSelect,
    IonSelectOption,
    IonInput,
  ],
  templateUrl: './mark-paid-modal.html',
  styleUrl: './mark-paid-modal.scss',
})
export class MarkPaidModal {
  @Input() memberName = '';

  // Mismo criterio que plan.highlight en member.ts (member.html línea 94): el primer plan de
  // la lista queda preseleccionado, igual que ya hacía el ion-alert que este modal reemplaza
  // (checked: index === 0).
  @Input() set plans(value: GymPlan[]) {
    this._plans.set(value);
    const current = this._selectedPlanId();
    if (current === null || !value.some((plan) => plan.id === current)) {
      const preferred = this._preferredPlanId !== null && value.some((plan) => plan.id === this._preferredPlanId);
      this._selectedPlanId.set(preferred ? this._preferredPlanId : (value[0]?.id ?? null));
    }
  }

  // Plan actual del socio (al renovar): queda preseleccionado si está entre los planes ofrecidos.
  // Se puede pasar antes o después de `plans`; solo manda mientras el usuario no haya elegido otro.
  private _preferredPlanId: number | null = null;
  @Input() set currentPlanId(value: number | null | undefined) {
    this._preferredPlanId = value ?? null;
    if (this._preferredPlanId !== null && !this.userPickedPlan && this._plans().some((p) => p.id === this._preferredPlanId)) {
      this._selectedPlanId.set(this._preferredPlanId);
    }
  }
  private userPickedPlan = false;
  /** Título del modal: "Marcar pago" o "Renovar plan". */
  @Input() renewing = false;

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  // planId + medio de pago (el banco si fue transferencia, o "Efectivo" / "Otro medio"; queda guardado para informes, ver ManualPayment).
  // startDate null = hoy (el servidor arranca el plan ahora, como siempre); usedClasses 0 = ninguna.
  @Output() confirmed = new EventEmitter<{
    planId: number;
    bank: string;
    startDate: string | null;
    usedClasses: number;
    confirmLoseQueuedMonths?: boolean;
  }>();

  // El servidor respondió 409 LOSE_QUEUED_MONTHS (la vista previa no alcanzó a avisarlo): fuerza el aviso y la casilla.
  @Input() set requireLoseConfirm(value: boolean) {
    this._requireLoseConfirm.set(value);
  }
  private readonly _requireLoseConfirm = signal(false);

  /** Pide al servidor la vista previa (período y clases que le quedarían) — la pasa el panel con su propio servicio. */
  @Input() previewLoader: ((planId: number, startDate: string, usedClasses: number) => Observable<PaymentPreview>) | null = null;
  @Output() dismiss = new EventEmitter<void>();

  private readonly _plans = signal<GymPlan[]>([]);
  protected readonly plansList = this._plans.asReadonly();

  private readonly _selectedPlanId = signal<number | null>(null);
  protected readonly selectedPlanId = this._selectedPlanId.asReadonly();

  private readonly destroyRef = inject(DestroyRef);

  // ---- fecha de inicio y clases ya usadas ----
  protected readonly today = santiagoNow().date;
  protected readonly minDate = minStartDate(this.today);
  protected readonly shortcuts = startDateShortcuts(this.today);
  protected readonly startDate = signal(this.today);
  protected readonly usedClasses = signal(0);
  protected readonly preview = signal<PaymentPreview | null>(null);
  protected readonly previewError = signal<string | null>(null);
  protected readonly previewing = signal(false);

  private readonly selectedPlan = computed(() => this._plans().find((p) => p.id === this._selectedPlanId()) ?? null);
  // Los planes libres no tienen cupo: no hay "clases usadas".
  protected readonly quota = computed(() => this.selectedPlan()?.monthlyClasses ?? null);
  // El período se muestra siempre, calculado acá; lo que pone el servidor (clases que le quedan) es un extra: si la
  // vista previa falla por algo ajeno al usuario, el resumen sigue mostrando las fechas y no se bloquea nada.
  // Con renovación por adelantado, la vista previa trae el período NUEVO (parte en el vencimiento actual).
  protected readonly earlyRenewal = computed(() => this.preview()?.earlyRenewal === true);
  protected readonly planChangeScheduled = computed(() => this.preview()?.planChangeScheduled === true);
  protected readonly planChangeLabel = computed(() => {
    const p = this.preview();
    return p?.planChangeScheduled && p.planChangeFrom ? formatIsoDate(p.planChangeFrom) : '';
  });
  protected readonly earlyStartLabel = computed(() => {
    const p = this.preview();
    return p?.earlyRenewal ? formatIsoDate(p.periodStart) : '';
  });
  protected readonly periodLine = computed(() => {
    const p = this.preview();
    if (p?.earlyRenewal) {
      return `Período nuevo: del ${formatIsoDate(p.periodStart)} al ${formatIsoDate(p.periodEnd)}`;
    }
    return periodText({ periodStart: this.startDate(), periodEnd: periodEndOf(this.startDate()) });
  });
  protected readonly remainingLine = computed(() => (this.preview() ? remainingText(this.preview()!) : null));
  protected readonly outOfClasses = computed(() => {
    const p = this.preview();
    return !!p && p.remaining !== null && p.remaining <= 0;
  });

  // Meses ya pagados por adelantado que se descartarían: exige marcar la casilla antes de confirmar.
  protected readonly lostMonths = computed(() => this.preview()?.lostPaidMonths ?? 0);
  protected readonly lostDays = computed(() => this.preview()?.lostDays ?? 0);
  protected readonly mustAckLoss = computed(() => this.lostMonths() > 0 || this._requireLoseConfirm());
  protected readonly loseAck = signal(false);
  // Aclaración bajo el selector de fecha: con otra fecha el período se reinicia (solo si el socio ya tiene plan).
  protected readonly showRestartHint = computed(() => this.renewing && this.startDate() !== this.today);

  private previewTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Cada cambio de plan, fecha o clases pide la vista previa (con debounce, y la respuesta vieja se descarta).
    effect(() => {
      const planId = this._selectedPlanId();
      const start = this.startDate();
      const used = this.usedClasses();
      untracked(() => {
        // Cambió algo que recalcula la vista previa: la confirmación anterior ya no vale.
        this.loseAck.set(false);
        this.schedulePreview(planId, start, used);
      });
    });
    this.destroyRef.onDestroy(() => clearTimeout(this.previewTimer));
  }

  private schedulePreview(planId: number | null, start: string, used: number): void {
    clearTimeout(this.previewTimer);
    if (planId === null || !this.previewLoader) {
      this.preview.set(null);
      return;
    }
    this.previewing.set(true);
    this.previewTimer = setTimeout(() => {
      this.previewLoader!(planId, start, this.quota() === null ? 0 : used).subscribe({
        next: (preview) => {
          // Si mientras tanto cambió algo, esta respuesta ya no corresponde.
          if (planId === this._selectedPlanId() && start === this.startDate() && used === this.usedClasses()) {
            this.preview.set(preview);
            this.previewError.set(null);
            this.previewing.set(false);
          }
        },
        error: (err: { status?: number; message?: string }) => {
          this.preview.set(null);
          this.previewing.set(false);
          // Solo un rechazo por las reglas (400/409/422: fecha futura, clases de más…) es un error del usuario y
          // bloquea; cualquier otro fallo no es asunto suyo — el servidor igual valida al confirmar.
          const actionable = err?.status === 400 || err?.status === 409 || err?.status === 422;
          this.previewError.set(actionable ? errorText(err, 'La fecha o las clases no son válidas.') : null);
        },
      });
    }, 300);
  }

  protected setStartDate(value: string): void {
    // Un input de fecha vacío o fuera de rango no se manda: queda la última fecha válida.
    if (value && value >= this.minDate && value <= this.today) {
      this.startDate.set(value);
    }
  }

  protected stepUsed(delta: number): void {
    const max = this.quota() ?? 0;
    this.usedClasses.update((n) => Math.min(Math.max(n + delta, 0), max));
  }

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  // Medio de pago: el banco solo se pide si fue transferencia. El servidor guarda un texto en `bank`
  // (obligatorio), así que efectivo y otro medio mandan su propio nombre.
  protected readonly methods = PAYMENT_METHODS;
  protected readonly method = signal<PaymentMethod>('transfer');

  // Misma lista y mismo patrón que los datos bancarios del gym: "Otro" abre texto libre.
  protected readonly banks = CHILE_BANKS;
  protected readonly bank = signal('');
  protected readonly bankOther = signal('');

  // Lo que efectivamente se guarda: el banco (o el texto libre de "Otro") si es transferencia; si no, el medio.
  private readonly resolvedBank = computed(() => {
    switch (this.method()) {
      case 'cash':
        return 'Efectivo';
      case 'other':
        return 'Otro medio';
      default:
        return this.bank() === 'Otro' ? this.bankOther().trim() : this.bank();
    }
  });

  // Con transferencia sin banco no se puede confirmar (el dato es obligatorio para informes).
  // Tampoco se confirma con una fecha o clases que el servidor rechazó (previewError trae su motivo).
  protected readonly canConfirm = computed(
    () => this._selectedPlanId() !== null && this.resolvedBank() !== '' && !this._saving() && !this.previewError() && (!this.mustAckLoss() || this.loseAck()),
  );

  // Mismo texto/formato exacto que join.ts (página pública de alta) — el usuario pidió que
  // el picker de "Marcar pago" se vea igual al bloque "Planes disponibles" que ya se le
  // muestra al socio antes de unirse, no la variante "X clases/mes" del resto de gym-admin.
  protected quotaLabel(plan: GymPlan): string {
    return plan.monthlyClasses === null ? 'Clases ilimitadas' : `${plan.monthlyClasses} clases al mes`;
  }

  protected formatClp(value: number): string {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(value);
  }

  protected selectPlan(plan: GymPlan): void {
    this.userPickedPlan = true;
    this._selectedPlanId.set(plan.id);
    // Al cambiar de plan, las clases ya usadas no pueden pasar del cupo nuevo (ni existir en un plan libre).
    const max = plan.monthlyClasses ?? 0;
    this.usedClasses.update((n) => Math.min(n, max));
  }

  protected confirm(): void {
    const planId = this._selectedPlanId();
    const bank = this.resolvedBank();
    if (planId === null || bank === '' || this._saving() || (this.mustAckLoss() && !this.loseAck())) {
      return;
    }
    this.confirmed.emit({
      planId,
      bank,
      startDate: this.startDate() === this.today ? null : this.startDate(),
      usedClasses: this.quota() === null ? 0 : this.usedClasses(),
      ...(this.mustAckLoss() ? { confirmLoseQueuedMonths: true } : {}),
    });
  }

  protected onLoseAckChange(event: Event): void {
    this.loseAck.set((event.target as HTMLInputElement).checked);
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
