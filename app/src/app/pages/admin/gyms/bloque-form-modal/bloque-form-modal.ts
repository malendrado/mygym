import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonNote,
  IonSelect,
  IonSelectOption,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { timeOutline } from 'ionicons/icons';
import { CreateGymBlockRequest, DayOfWeek, GymBlock, UpdateGymBlockRequest } from '../../../../core/models/gym.model';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';
import { TIME_OPTIONS_15MIN } from '../../../../core/utils/time-options';

addIcons({ 'time-outline': timeOutline });

export const DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

function endAfterStartValidator(control: AbstractControl): ValidationErrors | null {
  const start = control.get('startTime')?.value;
  const end = control.get('endTime')?.value;
  if (start && end && end <= start) {
    return { endBeforeStart: true };
  }
  return null;
}

@Component({
  selector: 'app-bloque-form-modal',
  imports: [
    ReactiveFormsModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonItem,
    IonInput,
    IonSelect,
    IonSelectOption,
    IonNote,
    QuantityStepper,
  ],
  templateUrl: './bloque-form-modal.html',
  styleUrl: './bloque-form-modal.scss',
})
export class BloqueFormModal {
  protected readonly days = DAYS;
  protected readonly timeOptions = TIME_OPTIONS_15MIN;

  @Input() set block(value: GymBlock | null) {
    this._block.set(value);
    if (value) {
      this.form.patchValue({
        label: value.label,
        dayOfWeek: value.dayOfWeek,
        startTime: value.startTime,
        endTime: value.endTime,
        capacity: value.capacity,
      });
    } else {
      this.form.reset({
        label: '',
        dayOfWeek: 'MONDAY',
        startTime: '',
        endTime: '',
        capacity: 1,
      });
    }
  }

  @Input() set saving(value: boolean) {
    this._parentSaving.set(value);
    if (!value) {
      this._submitting.set(false);
    }
  }

  @Output() saved = new EventEmitter<CreateGymBlockRequest | UpdateGymBlockRequest>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _block = signal<GymBlock | null>(null);
  protected readonly isEditing = computed(() => this._block() !== null);

  private readonly _parentSaving = signal(false);
  private readonly _submitting = signal(false);
  protected readonly isBusy = computed(() => this._parentSaving() || this._submitting());

  protected readonly form = new FormGroup(
    {
      label: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
      dayOfWeek: new FormControl<DayOfWeek>('MONDAY', { nonNullable: true, validators: [Validators.required] }),
      startTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      endTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      capacity: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(500)] }),
    },
    { validators: endAfterStartValidator },
  );

  protected selectDay(day: DayOfWeek): void {
    this.form.controls.dayOfWeek.setValue(day);
    this.form.controls.dayOfWeek.markAsTouched();
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this._submitting.set(true);
    const value = this.form.getRawValue();
    const current = this._block();
    if (current) {
      this.saved.emit({ ...value, active: current.active });
    } else {
      this.saved.emit(value);
    }
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
