import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonButton,
  IonButtons,
  IonCheckbox,
  IonContent,
  IonFooter,
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
import { trackDirty } from '../../../../core/utils/dirty-tracker';
import { CreateGymBlockRequest, DayOfWeek, GymBlock } from '../../../../core/models/gym.model';
import { DAYS } from '../bloque-form-modal/bloque-form-modal';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';
import { TIME_OPTIONS_15MIN } from '../../../../core/utils/time-options';

addIcons({ 'time-outline': timeOutline, 'sparkles-outline': sparklesOutline, 'close-circle-outline': closeCircleOutline });

const DURATIONS_MIN = [30, 45, 60, 90, 120];

interface DraftBlock extends CreateGymBlockRequest {
  dayLabel: string;
  /** Ya hay un bloque igual (mismo día, horario y nombre): queda desmarcado para no duplicarlo. */
  exists: boolean;
  selected: boolean;
}

function hhmm(time: string): string {
  return time.slice(0, 5);
}

function normalizeLabel(label: string | null | undefined): string {
  return (label ?? '').trim().toLowerCase();
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
    IonFooter,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonCheckbox,
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

  /** Bloques que ya tiene el gimnasio: la vista previa marca (y desmarca) los que coinciden para no duplicarlos. */
  @Input() set existing(value: GymBlock[] | null | undefined) {
    this.existingKeys.set(new Set((value ?? []).map((b) => this.blockKey(b.dayOfWeek, b.startTime, b.endTime, b.label))));
  }
  private readonly existingKeys = signal<Set<string>>(new Set());

  @Output() confirmed = new EventEmitter<CreateGymBlockRequest[]>();
  @Output() dismiss = new EventEmitter<void>();
  /** Hay cambios sin guardar (el padre lo usa para no cerrar el modal sin avisar). */
  @Output() dirtyChange = new EventEmitter<boolean>();

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
  protected readonly selectedCount = computed(() => this.draft().filter((d) => d.selected).length);
  protected readonly existsCount = computed(() => this.draft().filter((d) => d.exists).length);
  protected readonly hasGenerated = computed(() => this.draft().length > 0 || this.generatedOnce());
  private readonly generatedOnce = signal(false);

  protected readonly scheduleInvalid = computed(() => {
    const value = this.form.getRawValue();
    return toMinutes(value.closeTime) <= toMinutes(value.openTime);
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: null });
  private readonly dirtyTracker = trackDirty(
    () => {
      this.formValue();
      return [this.form.getRawValue(), this.draft().length];
    },
    (dirty) => this.dirtyChange.emit(dirty),
  );

  constructor() {
    this.dirtyTracker.markClean();
  }

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
    const existingKeys = this.existingKeys();
    for (const day of days) {
      const dayLabel = this.days.find((d) => d.value === day)?.label ?? day;
      let cursor = openMin;
      while (cursor + durationMin <= closeMin) {
        const startTime = toTimeString(cursor);
        const endTime = toTimeString(cursor + durationMin);
        const exists = existingKeys.has(this.blockKey(day, startTime, endTime, label));
        drafts.push({
          label,
          dayOfWeek: day,
          startTime,
          endTime,
          capacity,
          category: null,
          instructorName: null,
          instructorPhoto: null,
          dayLabel,
          exists,
          selected: !exists,
        });
        cursor += durationMin;
      }
    }
    this.draft.set(drafts);
  }

  protected setEntrySelected(entry: DraftBlock, selected: boolean): void {
    this.draft.set(this.draft().map((d) => (d === entry ? { ...d, selected } : d)));
  }

  private blockKey(day: string, start: string, end: string, label: string | null | undefined): string {
    return `${day}|${hhmm(start)}|${hhmm(end)}|${normalizeLabel(label)}`;
  }

  protected confirm(): void {
    const chosen = this.draft().filter((d) => d.selected);
    if (chosen.length === 0) {
      return;
    }
    this.confirmed.emit(chosen.map(({ dayLabel, exists, selected, ...rest }) => rest));
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
