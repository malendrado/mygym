import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonNote,
  IonTextarea,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { pricetagOutline } from 'ionicons/icons';
import { CreateGymPlanRequest, GymPlan, UpdateGymPlanRequest } from '../../../../core/models/gym.model';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';

addIcons({ 'pricetag-outline': pricetagOutline });

export interface PlanTemplate {
  key: string;
  label: string;
  category: string;
  name: string;
  description: string;
  /** Null = plan libre (reserva ilimitada). */
  monthlyClasses: number | null;
  suggestedPriceClp: number;
}

// Sugerencias de plan — puntos de partida típicos de un gimnasio, no
// categorías cerradas: elegir una precarga nombre/descripción/cupo/precio,
// pero cada campo queda editable. El admin siempre define el precio final.
export const PLAN_TEMPLATES: PlanTemplate[] = [
  {
    key: 'personal',
    label: 'Personal Training',
    category: 'personal',
    name: 'Personal Training',
    description: 'Entrenamiento 1 a 1 con seguimiento personalizado.',
    monthlyClasses: 8,
    suggestedPriceClp: 89990,
  },
  {
    key: 'guiado',
    label: 'Nivel medio',
    category: 'guiado',
    name: 'Nivel Medio',
    description: 'Clases grupales acompañadas por un coach.',
    monthlyClasses: 12,
    suggestedPriceClp: 39990,
  },
  {
    key: 'libre',
    label: 'Plan libre',
    category: 'libre',
    name: 'Libre',
    description: 'Reserva todas las clases que quieras en los cupos disponibles.',
    monthlyClasses: null,
    suggestedPriceClp: 49990,
  },
  {
    key: 'adulto_mayor',
    label: 'Adulto mayor',
    category: 'adulto_mayor',
    name: 'Adulto Mayor',
    description: 'Tarifa preferencial para socios de la tercera edad.',
    monthlyClasses: 12,
    suggestedPriceClp: 24990,
  },
  {
    key: 'estudiante',
    label: 'Estudiante',
    category: 'estudiante',
    name: 'Estudiante',
    description: 'Tarifa preferencial para estudiantes con credencial vigente.',
    monthlyClasses: 12,
    suggestedPriceClp: 27990,
  },
];

@Component({
  selector: 'app-plan-form-modal',
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
    IonTextarea,
    IonNote,
    QuantityStepper,
  ],
  templateUrl: './plan-form-modal.html',
  styleUrl: './plan-form-modal.scss',
})
export class PlanFormModal {
  protected readonly templates = PLAN_TEMPLATES;

  @Input() set plan(value: GymPlan | null) {
    this._plan.set(value);
    if (value) {
      this.form.patchValue({
        name: value.name,
        description: value.description ?? '',
        priceClp: value.priceClp,
        monthlyClasses: value.monthlyClasses ?? 8,
      });
      this._selectedCategory.set(value.category);
      this._unlimited.set(value.monthlyClasses === null);
    } else {
      this.form.reset({ name: '', description: '', priceClp: null, monthlyClasses: 8 });
      this._selectedCategory.set(null);
      this._unlimited.set(false);
    }
  }

  @Input() set saving(value: boolean) {
    this._parentSaving.set(value);
    if (!value) {
      this._submitting.set(false);
    }
  }

  @Output() saved = new EventEmitter<CreateGymPlanRequest | UpdateGymPlanRequest>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _plan = signal<GymPlan | null>(null);
  protected readonly isEditing = computed(() => this._plan() !== null);

  private readonly _parentSaving = signal(false);
  private readonly _submitting = signal(false);
  protected readonly isBusy = computed(() => this._parentSaving() || this._submitting());

  private readonly _selectedCategory = signal<string | null>(null);
  protected readonly selectedCategory = this._selectedCategory.asReadonly();

  private readonly _unlimited = signal(false);
  protected readonly unlimited = this._unlimited.asReadonly();

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(280)] }),
    // Empieza en null (no 0): un 0 real deja pasar Validators.required igual
    // que un valor válido, así que un admin que se salta este campo terminaría
    // guardando un plan gratis sin darse cuenta. null lo bloquea de verdad, y
    // el input queda vacío (con el placeholder visible) en vez de mostrar "0".
    priceClp: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    monthlyClasses: new FormControl(8, { nonNullable: true, validators: [Validators.min(1), Validators.max(500)] }),
  });

  protected applyTemplate(template: PlanTemplate): void {
    this._selectedCategory.set(template.category);
    this._unlimited.set(template.monthlyClasses === null);
    this.form.patchValue({
      name: template.name,
      description: template.description,
      priceClp: template.suggestedPriceClp,
      monthlyClasses: template.monthlyClasses ?? this.form.controls.monthlyClasses.value,
    });
  }

  protected clearTemplate(): void {
    this._selectedCategory.set(null);
  }

  protected toggleUnlimited(): void {
    this._unlimited.update((value) => !value);
  }

  protected formatClp(value: number): string {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this._submitting.set(true);
    const { name, description, priceClp, monthlyClasses } = this.form.getRawValue();
    const payload = {
      name,
      description: description || null,
      category: this._selectedCategory(),
      priceClp: priceClp!, // form.invalid already returned above — priceClp is guaranteed non-null here.
      monthlyClasses: this._unlimited() ? null : monthlyClasses,
    };
    const current = this._plan();
    if (current) {
      this.saved.emit({ ...payload, active: current.active });
    } else {
      this.saved.emit(payload);
    }
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
