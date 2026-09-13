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
  IonFooter,
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
import { CLASS_CATEGORY_OPTIONS, registerClassCategoryIcons } from '../../../../core/utils/class-category';

addIcons({ 'time-outline': timeOutline });
registerClassCategoryIcons();

const MAX_INSTRUCTOR_PHOTO_DIMENSION = 200;
const ACCEPTED_INSTRUCTOR_PHOTO_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

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
    IonFooter,
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
  protected readonly categoryOptions = CLASS_CATEGORY_OPTIONS;

  private readonly _instructorPhoto = signal<string | null>(null);
  protected readonly instructorPhoto = this._instructorPhoto.asReadonly();
  protected readonly instructorPhotoUploading = signal(false);

  @Input() set block(value: GymBlock | null) {
    this._block.set(value);
    this._instructorPhoto.set(value?.instructorPhoto ?? null);
    if (value) {
      this.form.patchValue({
        label: value.label,
        dayOfWeek: value.dayOfWeek,
        // El backend siempre devuelve la hora con segundos ("09:00:00", el
        // formato de serialización por defecto de LocalTime en Jackson),
        // pero las opciones del select son "HH:mm" (sin segundos) — sin este
        // recorte, el valor patcheado no coincide con ninguna opción, el
        // select se ve vacío al editar, y si el usuario llega a tocarlo el
        // control queda en '' (inválido), bloqueando "Guardar" en silencio
        // (sin ningún mensaje de error visible). Bug real reportado por el
        // usuario 2026-09-13.
        startTime: value.startTime.slice(0, 5),
        endTime: value.endTime.slice(0, 5),
        capacity: value.capacity,
        category: value.category ?? '',
        instructorName: value.instructorName ?? '',
      });
    } else {
      this.form.reset({
        label: '',
        dayOfWeek: 'MONDAY',
        startTime: '',
        endTime: '',
        capacity: 1,
        category: '',
        instructorName: '',
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
      category: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
      instructorName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    },
    { validators: endAfterStartValidator },
  );

  protected selectDay(day: DayOfWeek): void {
    this.form.controls.dayOfWeek.setValue(day);
    this.form.controls.dayOfWeek.markAsTouched();
  }

  protected selectCategory(label: string): void {
    const current = this.form.controls.category.value;
    this.form.controls.category.setValue(current === label ? '' : label);
    this.form.controls.category.markAsTouched();
  }

  protected async onInstructorPhotoSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    if (!ACCEPTED_INSTRUCTOR_PHOTO_TYPES.includes(file.type)) {
      return;
    }
    this.instructorPhotoUploading.set(true);
    try {
      const data = await this.resizeInstructorPhoto(file);
      this._instructorPhoto.set(data);
    } catch {
      // Silencioso: es un campo opcional, no bloquea guardar el bloque.
    } finally {
      this.instructorPhotoUploading.set(false);
    }
  }

  protected removeInstructorPhoto(): void {
    this._instructorPhoto.set(null);
  }

  private resizeInstructorPhoto(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('file read error'));
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('image decode error'));
        img.onload = () => {
          const scale = Math.min(
            MAX_INSTRUCTOR_PHOTO_DIMENSION / img.width,
            MAX_INSTRUCTOR_PHOTO_DIMENSION / img.height,
            1,
          );
          const width = Math.max(1, Math.round(img.width * scale));
          const height = Math.max(1, Math.round(img.height * scale));
          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            reject(new Error('canvas not supported'));
            return;
          }
          ctx.drawImage(img, 0, 0, width, height);
          resolve(canvas.toDataURL('image/jpeg', 0.85));
        };
        img.src = reader.result as string;
      };
      reader.readAsDataURL(file);
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this._submitting.set(true);
    const raw = this.form.getRawValue();
    const value = {
      ...raw,
      category: raw.category || null,
      instructorName: raw.instructorName || null,
      instructorPhoto: this._instructorPhoto(),
    };
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
