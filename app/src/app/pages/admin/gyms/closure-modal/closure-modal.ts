import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
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
import { alertCircleOutline, banOutline, warningOutline } from 'ionicons/icons';
import { GymBlock, GymClosureCreateRequest, GymClosurePreview, sortBlocksBySchedule } from '../../../../core/models/gym.model';

addIcons({ 'ban-outline': banOutline, 'warning-outline': warningOutline, 'alert-circle-outline': alertCircleOutline });

// Mismo criterio de huso horario que el resto de la app (ver gym-admin.ts) — nunca
// `new Date(isoString)`, para no repetir el bug de interpretación UTC ya encontrado ahí.
function todayIsoDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

/**
 * Cierre de emergencia de gimnasio — compartido entre gym-admin.ts (dueño del gym) y gym-form.ts
 * (super-admin), mismo patrón que MarkPaidModal/PlanFormModal: el modal arma el request y lo
 * emite, pero nunca llama a la API directamente — el padre decide si pega al endpoint de
 * gym-admin o al de super-admin (gymId por JWT vs. por path).
 *
 * Flujo en dos pasos: 1) formulario (fechas + alcance + motivo), 2) vista previa de impacto
 * ("Ver impacto" dispara `preview`, el padre llama al endpoint y actualiza `previewResult`)
 * antes de poder confirmar — es una acción irreversible (cancela reservas reales), nunca se
 * confirma a ciegas.
 */
@Component({
  selector: 'app-closure-modal',
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
  templateUrl: './closure-modal.html',
  styleUrl: './closure-modal.scss',
})
export class ClosureModal {
  @Input() set blocks(value: GymBlock[]) {
    this._blocks.set(sortBlocksBySchedule(value ?? []));
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

  @Output() preview = new EventEmitter<GymClosureCreateRequest>();
  @Output() confirmed = new EventEmitter<GymClosureCreateRequest>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _blocks = signal<GymBlock[]>([]);
  protected readonly blocksList = this._blocks.asReadonly();

  private readonly _previewResult = signal<GymClosurePreview | null>(null);
  protected readonly previewResultValue = this._previewResult.asReadonly();

  private readonly _previewing = signal(false);
  protected readonly isPreviewing = this._previewing.asReadonly();

  private readonly _previewError = signal<string | null>(null);
  protected readonly previewErrorValue = this._previewError.asReadonly();

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  protected readonly step = signal<'form' | 'confirm'>('form');
  protected readonly selectedBlockIds = signal<Set<number>>(new Set());

  protected readonly form = new FormGroup({
    startDate: new FormControl(todayIsoDate(), { nonNullable: true, validators: [Validators.required] }),
    endDate: new FormControl(todayIsoDate(), { nonNullable: true, validators: [Validators.required] }),
    wholeDays: new FormControl(true, { nonNullable: true }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
  });

  // Método plano, NO computed(): el FormGroup reactivo no es un signal, así que un computed()
  // que solo lee form.invalid/form.controls.x.value queda cacheado para siempre tras la primera
  // lectura (sin dependencias de signal que lo invaliden) — bug real encontrado probando la UI:
  // "Ver impacto" nunca se habilitaba aunque el formulario ya fuera válido. Un método se
  // reevalúa en cada ciclo de detección de cambios, igual que el resto de los modales del
  // proyecto usan `form.invalid` directo en el template (ver plan-form-modal.html).
  protected canAskPreview(): boolean {
    if (this.form.invalid) {
      return false;
    }
    if (this.form.controls.endDate.value < this.form.controls.startDate.value) {
      return false;
    }
    if (!this.form.controls.wholeDays.value && this.selectedBlockIds().size === 0) {
      return false;
    }
    return true;
  }

  protected toggleScope(wholeDays: boolean): void {
    this.form.controls.wholeDays.setValue(wholeDays);
  }

  protected toggleBlock(blockId: number): void {
    this.selectedBlockIds.update((current) => {
      const next = new Set(current);
      if (next.has(blockId)) {
        next.delete(blockId);
      } else {
        next.add(blockId);
      }
      return next;
    });
  }

  private buildRequest(): GymClosureCreateRequest {
    const raw = this.form.getRawValue();
    return {
      startDate: raw.startDate,
      endDate: raw.endDate,
      wholeDays: raw.wholeDays,
      blockIds: raw.wholeDays ? [] : Array.from(this.selectedBlockIds()),
      reason: raw.reason.trim(),
    };
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
