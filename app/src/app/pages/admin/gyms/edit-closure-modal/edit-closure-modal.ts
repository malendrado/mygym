import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonNote,
  IonTextarea,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { alertCircleOutline, createOutline } from 'ionicons/icons';
import { GymClosure, GymClosurePreview, GymClosureUpdateRequest } from '../../../../core/models/gym.model';

addIcons({ 'create-outline': createOutline, 'alert-circle-outline': alertCircleOutline });

/**
 * Editar un cierre ya creado (ver GymClosureService.update) — a propósito solo permite tocar la
 * fecha de término y el motivo; el alcance (día completo vs. clases puntuales) y la fecha de
 * inicio quedan fijos desde la creación, se muestran como texto de solo lectura. Pedido real del
 * usuario: "cerré 3 días por un corte de luz, pero lo arreglan en 2" (acortar) o "van a tardar
 * más de lo que pensé" (alargar) — a diferencia de "Levantar" (reabre desde HOY), esto deja
 * elegir cualquier fecha de término, incluso una que todavía no llegó.
 *
 * Mismo patrón en dos pasos que ClosureModal (formulario → vista previa → confirmar) — si se
 * alarga el cierre, la vista previa muestra cuántas reservas NUEVAS se van a cancelar (0 si se
 * acorta o el término no cambió).
 */
@Component({
  selector: 'app-edit-closure-modal',
  imports: [
    ReactiveFormsModule,
    IonHeader,
    IonFooter,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonItem,
    IonLabel,
    IonNote,
    IonTextarea,
  ],
  templateUrl: './edit-closure-modal.html',
  styleUrl: './edit-closure-modal.scss',
})
export class EditClosureModal {
  @Input() set closure(value: GymClosure | null) {
    this._closure.set(value);
    if (value) {
      this.form.setValue({ endDate: value.endDate, reason: value.reason });
    }
  }

  @Input() set previewResult(value: GymClosurePreview | null) {
    this._previewResult.set(value);
  }

  @Input() set previewing(value: boolean) {
    this._previewing.set(value);
  }

  @Input() set previewError(value: string | null) {
    this._previewError.set(value ?? null);
  }

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  @Output() preview = new EventEmitter<GymClosureUpdateRequest>();
  @Output() confirmed = new EventEmitter<GymClosureUpdateRequest>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _closure = signal<GymClosure | null>(null);
  protected readonly closureValue = this._closure.asReadonly();

  protected readonly scopeLabel = computed(() => {
    const c = this._closure();
    if (!c) {
      return '';
    }
    return c.wholeDays ? 'Día completo' : `${c.blockIds.length} clase(s) puntual(es)`;
  });

  private readonly _previewResult = signal<GymClosurePreview | null>(null);
  protected readonly previewResultValue = this._previewResult.asReadonly();

  private readonly _previewing = signal(false);
  protected readonly isPreviewing = this._previewing.asReadonly();

  private readonly _previewError = signal<string | null>(null);
  protected readonly previewErrorValue = this._previewError.asReadonly();

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  protected readonly step = signal<'form' | 'confirm'>('form');

  protected readonly form = new FormGroup({
    endDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
  });

  // Método plano, NO computed() — el FormGroup reactivo no es un signal (mismo bug real ya
  // encontrado y corregido en ClosureModal: un computed() que solo lee form.invalid queda
  // cacheado para siempre tras la primera lectura).
  protected canAskPreview(): boolean {
    const closure = this._closure();
    if (!closure || this.form.invalid) {
      return false;
    }
    return !this.form.controls.endDate.value || this.form.controls.endDate.value >= closure.startDate;
  }

  protected isExtending(): boolean {
    const closure = this._closure();
    return !!closure && this.form.controls.endDate.value > closure.endDate;
  }

  private buildRequest(): GymClosureUpdateRequest {
    const raw = this.form.getRawValue();
    return { endDate: raw.endDate, reason: raw.reason.trim() };
  }

  protected askPreview(): void {
    if (!this.canAskPreview()) {
      return;
    }
    this.step.set('confirm');
    this.preview.emit(this.buildRequest());
  }

  protected backToForm(): void {
    this.step.set('form');
  }

  protected confirm(): void {
    this.confirmed.emit(this.buildRequest());
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
