import { Component, EventEmitter, Input, Output, computed, effect, signal, untracked } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { searchOutline } from 'ionicons/icons';
import { MemberWorkoutLog } from '../../models/workout.model';
import { WorkoutLogFilter, dayOptions, isDeviated } from '../../utils/workout-history-filter';

addIcons({ 'search-outline': searchOutline });

/** Buscador + chips para el historial de rutina (profesor/admin y socio). Solo actúa sobre los registros ya cargados:
 *  emite el filtro y cada pantalla lo aplica con `filterWorkoutLogs`. Se muestra recién cuando hay algo que filtrar. */
@Component({
  selector: 'app-workout-history-filter',
  imports: [IonIcon],
  templateUrl: './workout-history-filter.html',
  styleUrl: './workout-history-filter.scss',
})
export class WorkoutHistoryFilter {
  private readonly _logs = signal<MemberWorkoutLog[]>([]);

  /** Registros ya cargados. */
  @Input({ required: true }) set logs(value: MemberWorkoutLog[]) {
    this._logs.set(value);
  }

  /** Quedan registros más antiguos por cargar: el filtro vale la pena aunque haya pocos cargados. */
  private readonly _hasMore = signal(false);
  @Input() set hasMore(value: boolean) {
    this._hasMore.set(value);
  }

  @Output() filterChange = new EventEmitter<WorkoutLogFilter>();

  protected readonly day = signal<string | null>(null);
  protected readonly onlyDeviated = signal(false);
  protected readonly query = signal('');

  protected readonly days = computed(() => dayOptions(this._logs()));
  protected readonly hasDeviation = computed(() => this._logs().some(isDeviated));
  // Con 1-3 registros y nada más por cargar, un buscador es ruido.
  protected readonly visible = computed(() => this._logs().length >= 4 || this._hasMore());
  protected readonly allActive = computed(() => this.day() === null && !this.onlyDeviated());

  constructor() {
    // Si el día elegido ya no existe entre los cargados (p. ej. se recargó el historial), vuelve a "Todos".
    effect(() => {
      const current = this.day();
      if (current !== null && !this.days().includes(current)) {
        untracked(() => {
          this.day.set(null);
          this.emit();
        });
      }
    });
  }

  protected setQuery(value: string): void {
    this.query.set(value);
    this.emit();
  }

  protected toggleDay(day: string): void {
    this.day.set(this.day() === day ? null : day);
    this.emit();
  }

  protected toggleDeviated(): void {
    this.onlyDeviated.set(!this.onlyDeviated());
    this.emit();
  }

  protected showAll(): void {
    this.day.set(null);
    this.onlyDeviated.set(false);
    this.emit();
  }

  protected clearQuery(): void {
    this.setQuery('');
  }

  private emit(): void {
    this.filterChange.emit({ day: this.day(), onlyDeviated: this.onlyDeviated(), query: this.query() });
  }
}
