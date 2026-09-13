import { Component, EventEmitter, Output, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonSelect,
  IonSelectOption,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeCircleOutline, sparklesOutline, timeOutline } from 'ionicons/icons';
import { CreateGymBlockRequest, DayOfWeek } from '../../../../core/models/gym.model';
import { DAYS } from '../bloque-form-modal/bloque-form-modal';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';
import { TIME_OPTIONS_15MIN } from '../../../../core/utils/time-options';

addIcons({ 'time-outline': timeOutline, 'sparkles-outline': sparklesOutline, 'close-circle-outline': closeCircleOutline });

const DURATIONS_MIN = [30, 45, 60, 90, 120];

interface DraftBlock extends CreateGymBlockRequest {
  dayLabel: string;
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function toTimeString(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60)
    .toString()
    .padStart(2, '0');
  const m = (totalMinutes % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
}

@Component({
  selector: 'app-bloque-series-modal',
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
    IonLabel,
    IonInput,
    IonSelect,
    IonSelectOption,
    IonList,
    IonNote,
    QuantityStepper,
  ],
  templateUrl: './bloque-series-modal.html',
  styleUrl: './bloque-series-modal.scss',
})
export class BloqueSeriesModal {
  protected readonly days = DAYS;
  protected readonly durations = DURATIONS_MIN;
  protected readonly timeOptions = TIME_OPTIONS_15MIN;

  @Output() confirmed = new EventEmitter<CreateGymBlockRequest[]>();
  @Output() dismiss = new EventEmitter<void>();

  protected readonly form = new FormGroup({
    days: new FormControl<DayOfWeek[]>(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], {
      nonNullable: true,
    }),
    openTime: new FormControl('06:00', { nonNullable: true, validators: [Validators.required] }),
    closeTime: new FormControl('22:00', { nonNullable: true, validators: [Validators.required] }),
    durationMin: new FormControl<number>(60, { nonNullable: true, validators: [Validators.required] }),
    label: new FormControl('Clase funcional', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(60)],
    }),
    capacity: new FormControl(15, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(500)] }),
  });

  protected readonly draft = signal<DraftBlock[]>([]);
  protected readonly hasGenerated = computed(() => this.draft().length > 0 || this.generatedOnce());
  private readonly generatedOnce = signal(false);

  protected readonly scheduleInvalid = computed(() => {
    const value = this.form.getRawValue();
    return toMinutes(value.closeTime) <= toMinutes(value.openTime);
  });

  protected toggleDay(day: DayOfWeek, checked: boolean): void {
    const current = this.form.controls.days.value;
    this.form.controls.days.setValue(checked ? [...current, day] : current.filter((d) => d !== day));
  }

  protected isDaySelected(day: DayOfWeek): boolean {
    return this.form.controls.days.value.includes(day);
  }

  protected generatePreview(): void {
    this.generatedOnce.set(true);
    if (this.form.invalid || this.scheduleInvalid() || this.form.controls.days.value.length === 0) {
      this.draft.set([]);
      return;
    }
    const { days, openTime, closeTime, durationMin, label, capacity } = this.form.getRawValue();
    const openMin = toMinutes(openTime);
    const closeMin = toMinutes(closeTime);
    const drafts: DraftBlock[] = [];
    for (const day of days) {
      const dayLabel = this.days.find((d) => d.value === day)?.label ?? day;
      let cursor = openMin;
      while (cursor + durationMin <= closeMin) {
        drafts.push({
          label,
          dayOfWeek: day,
          startTime: toTimeString(cursor),
          endTime: toTimeString(cursor + durationMin),
          capacity,
          dayLabel,
        });
        cursor += durationMin;
      }
    }
    this.draft.set(drafts);
  }

  protected removeDraftEntry(entry: DraftBlock): void {
    this.draft.set(this.draft().filter((d) => d !== entry));
  }

  protected confirm(): void {
    if (this.draft().length === 0) {
      return;
    }
    this.confirmed.emit(this.draft().map(({ dayLabel, ...rest }) => rest));
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
