import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { IonButton, IonCard, IonCardContent, IonCardHeader, IonCardTitle, IonIcon, IonToggle } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { timerOutline } from 'ionicons/icons';
import { BookingRulesRequest } from '../../models/gym.model';
import { QuantityStepper } from '../quantity-stepper/quantity-stepper';

addIcons({ 'timer-outline': timerOutline });

/**
 * Tarjeta "Reglas de reserva" — va al INICIO de la pestaña Horarios, tanto en el panel del gym
 * (gym-admin) como en el del super-admin (gym-form): mismo markup y mismas validaciones, el padre
 * solo le pasa los valores actuales y recibe el payload ya validado en `save`.
 *
 * Los dos límites (reservar / cancelar) son datos DISTINTOS, en minutos (antes era un solo
 * campo en horas que servía para ambos). La diferencia se explica acá mismo, en la tarjeta.
 */
@Component({
  selector: 'app-booking-rules-card',
  imports: [
    ReactiveFormsModule,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonButton,
    IonIcon,
    IonToggle,
    QuantityStepper,
  ],
  templateUrl: './booking-rules-card.html',
  styleUrl: './booking-rules-card.scss',
})
export class BookingRulesCard {
  protected readonly maxMinutes = 10_080; // 7 días, mismo tope que valida el backend

  protected readonly form = new FormGroup({
    bookingWindowMinutes: new FormControl(120, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(this.maxMinutes)],
    }),
    cancellationWindowMinutes: new FormControl(120, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(this.maxMinutes)],
    }),
    waitlistHeadStartMinutes: new FormControl(30, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(this.maxMinutes)],
    }),
    showAttendeesToMembers: new FormControl(true, { nonNullable: true }),
  });

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  // Hasta que el padre trae los valores reales del gym, el formulario solo tiene los defaults:
  // guardar en ese instante pisaría la configuración real con valores que nadie eligió.
  protected readonly loaded = signal(false);

  @Input() set rules(value: BookingRulesRequest | null) {
    if (value) {
      this.form.patchValue(value);
      this.form.markAsPristine();
      this.loaded.set(true);
    }
  }

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  @Output() save = new EventEmitter<BookingRulesRequest>();

  protected submit(): void {
    if (this.form.invalid || !this.loaded()) {
      return;
    }
    this.save.emit(this.form.getRawValue());
  }

  // "2 h", "1 h 30 min", "45 min" — mismo formato que usan los avisos al socio en /member.
  protected label(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    if (hours === 0) {
      return `${rest} min`;
    }
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
  }
}
