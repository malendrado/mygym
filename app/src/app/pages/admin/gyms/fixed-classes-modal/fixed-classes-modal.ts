import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { IonButton, IonButtons, IonContent, IonFooter, IonHeader, IonIcon, IonSpinner, IonTitle, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { informationCircleOutline, repeatOutline } from 'ionicons/icons';
import { FixedClass } from '../../../../core/models/fixed-class.model';
import { DayOfWeek, GymBlock, sortBlocksBySchedule } from '../../../../core/models/gym.model';
import { dayLongLabel } from '../../../../core/utils/fixed-class-import';

addIcons({ 'repeat-outline': repeatOutline, 'information-circle-outline': informationCircleOutline });

interface DayGroup {
  day: DayOfWeek;
  label: string;
  blocks: GymBlock[];
}

/**
 * Clases fijas de UN socio: una clase por día de la semana como máximo (el pedido es "una clase
 * diaria"), así que cada día se comporta como un selector de una sola opción — tocar otra clase
 * del mismo día reemplaza la anterior, tocar la elegida la quita. Compartido entre gym-admin.ts
 * (dueño y profesor) y gym-form.ts (super-admin): el modal solo emite los bloques elegidos, nunca
 * llama a la API, mismo patrón que ClosureModal/MarkPaidModal.
 */
@Component({
  selector: 'app-fixed-classes-modal',
  imports: [IonHeader, IonFooter, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon, IonSpinner],
  templateUrl: './fixed-classes-modal.html',
  styleUrl: './fixed-classes-modal.scss',
})
export class FixedClassesModal {
  @Input() memberName = '';
  /** Texto bajo la lista: hasta cuándo se reserva, o que no tiene plan activo (lo arma el padre). */
  @Input() hint = '';

  @Input() set blocks(value: GymBlock[]) {
    this._blocks.set(sortBlocksBySchedule((value ?? []).filter((b) => b.active)));
  }

  @Input() set current(value: FixedClass[]) {
    const active = (value ?? []).filter((c) => c.active);
    this._inactive.set((value ?? []).filter((c) => !c.active));
    // día → bloque elegido
    const byDay = new Map<DayOfWeek, number>();
    active.forEach((c) => byDay.set(c.dayOfWeek, c.blockId));
    this.selectedByDay.set(byDay);
  }

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  @Output() saved = new EventEmitter<number[]>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _blocks = signal<GymBlock[]>([]);
  private readonly _inactive = signal<FixedClass[]>([]);
  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();
  protected readonly inactiveClasses = this._inactive.asReadonly();
  protected readonly selectedByDay = signal<Map<DayOfWeek, number>>(new Map());

  protected readonly groups = computed<DayGroup[]>(() => {
    const byDay = new Map<DayOfWeek, GymBlock[]>();
    for (const block of this._blocks()) {
      byDay.set(block.dayOfWeek, [...(byDay.get(block.dayOfWeek) ?? []), block]);
    }
    return Array.from(byDay, ([day, blocks]) => ({ day, label: dayLongLabel(day), blocks }));
  });

  protected readonly selectedCount = computed(() => this.selectedByDay().size);

  protected isSelected(block: GymBlock): boolean {
    return this.selectedByDay().get(block.dayOfWeek) === block.id;
  }

  protected selectedLabel(group: DayGroup): string | null {
    const id = this.selectedByDay().get(group.day);
    const block = group.blocks.find((b) => b.id === id);
    return block ? `${block.startTime.slice(0, 5)} ${block.label}` : null;
  }

  protected toggle(block: GymBlock): void {
    this.selectedByDay.update((current) => {
      const next = new Map(current);
      if (next.get(block.dayOfWeek) === block.id) {
        next.delete(block.dayOfWeek);
      } else {
        next.set(block.dayOfWeek, block.id);
      }
      return next;
    });
  }

  protected clearAll(): void {
    this.selectedByDay.set(new Map());
  }

  protected blockTime(block: GymBlock): string {
    return `${block.startTime.slice(0, 5)} – ${block.endTime.slice(0, 5)}`;
  }

  protected blockDetail(block: GymBlock): string {
    return [block.category, block.instructorName].filter(Boolean).join(' · ');
  }

  protected readonly inactiveText = computed(() =>
    this._inactive()
      .map((c) => `${dayLongLabel(c.dayOfWeek)} ${c.startTime.slice(0, 5)} ${c.label}`)
      .join(', '),
  );

  protected save(): void {
    if (this._saving()) {
      return;
    }
    this.saved.emit(Array.from(this.selectedByDay().values()));
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
