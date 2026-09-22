import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { IonButton, IonButtons, IonContent, IonFooter, IonHeader, IonIcon, IonTitle, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { cardOutline, checkmarkCircle } from 'ionicons/icons';
import { GymPlan } from '../../../../core/models/gym.model';

addIcons({ 'card-outline': cardOutline, 'checkmark-circle': checkmarkCircle });

@Component({
  selector: 'app-mark-paid-modal',
  imports: [IonHeader, IonFooter, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon],
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
      this._selectedPlanId.set(value[0]?.id ?? null);
    }
  }

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  @Output() confirmed = new EventEmitter<number>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _plans = signal<GymPlan[]>([]);
  protected readonly plansList = this._plans.asReadonly();

  private readonly _selectedPlanId = signal<number | null>(null);
  protected readonly selectedPlanId = this._selectedPlanId.asReadonly();

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  protected readonly canConfirm = computed(() => this._selectedPlanId() !== null && !this._saving());

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
    this._selectedPlanId.set(plan.id);
  }

  protected confirm(): void {
    const planId = this._selectedPlanId();
    if (planId === null) {
      return;
    }
    this.confirmed.emit(planId);
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
