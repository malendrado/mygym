import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { addCircleOutline, barbellOutline, closeCircleOutline, informationCircleOutline } from 'ionicons/icons';
import { WorkoutHistoryFilter } from '../../../../core/components/workout-history-filter/workout-history-filter';
import { CreateWorkoutPlanRequest, MemberWorkoutLog, WorkoutPlan } from '../../../../core/models/workout.model';
import {
  NO_WORKOUT_FILTER,
  WorkoutLogFilter,
  filterWorkoutLogs,
  highlightParts,
  isFiltering,
} from '../../../../core/utils/workout-history-filter';

addIcons({
  'barbell-outline': barbellOutline,
  'add-circle-outline': addCircleOutline,
  'close-circle-outline': closeCircleOutline,
  'information-circle-outline': informationCircleOutline,
});

/** Panel del profesor de "Memoria Viva" (ver diseño: MODAL, consistente con el resto de
 *  gym-admin) — edita la pauta cíclica de un socio y muestra su último registro ("la memoria")
 *  antes de hablar con él. Redefinir la rutina es SIEMPRE reemplazar entera (desactiva la
 *  anterior, crea una nueva) — decisión explícita del usuario, los logs viejos quedan intactos
 *  apuntando al plan anterior, como historia. */
@Component({
  selector: 'app-workout-plan-modal',
  imports: [
    IonHeader,
    IonFooter,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonList,
    IonItem,
    IonLabel,
    IonInput,
    WorkoutHistoryFilter,
  ],
  templateUrl: './workout-plan-modal.html',
  styleUrl: './workout-plan-modal.scss',
})
export class WorkoutPlanModal {
  @Input() memberName = '';

  @Input() set plan(value: WorkoutPlan | null) {
    this._name.set(value?.name ?? '');
    this._dayTitles.set(value && value.days.length > 0 ? value.days.map((d) => d.title) : ['']);
  }

  /** Registros ya cargados del socio, el más reciente primero (se pide de a 7 días). */
  @Input() set history(value: MemberWorkoutLog[]) {
    this._history.set(value);
  }

  /** Hay registros más antiguos por cargar (el backend entregó un cursor). */
  @Input() hasMoreHistory = false;
  @Input() loadingMoreHistory = false;
  @Output() loadMoreHistory = new EventEmitter<void>();

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  @Output() saved = new EventEmitter<CreateWorkoutPlanRequest>();
  @Output() dismiss = new EventEmitter<void>();

  private readonly _name = signal('');
  protected readonly name = this._name.asReadonly();

  private readonly _dayTitles = signal<string[]>(['']);
  protected readonly dayTitles = this._dayTitles.asReadonly();

  private readonly _history = signal<MemberWorkoutLog[]>([]);
  protected readonly historyValue = this._history.asReadonly();

  // Buscador y chips: actúan solo sobre los registros ya cargados.
  private readonly _filter = signal<WorkoutLogFilter>(NO_WORKOUT_FILTER);
  protected readonly filtering = computed(() => isFiltering(this._filter()));
  protected readonly visibleHistory = computed(() => filterWorkoutLogs(this._history(), this._filter()));
  protected readonly latestId = computed(() => this._history()[0]?.id ?? null);

  protected setFilter(filter: WorkoutLogFilter): void {
    this._filter.set(filter);
  }

  protected parts(text: string | null) {
    return highlightParts(text ?? '', this._filter().query);
  }

  private readonly _saving = signal(false);
  protected readonly isSaving = this._saving.asReadonly();

  // Se desvió si escribió texto libre (planDayId null) o si eligió un día distinto al sugerido —
  // en ambos casos el profesor necesita ver "qué pasó realmente" antes de hablar con el socio.
  protected deviated(log: MemberWorkoutLog): boolean {
    return log.planDayId === null || log.planDayId !== log.suggestedPlanDayId;
  }

  protected doneLabel(log: MemberWorkoutLog): string {
    return (log.planDayId !== null ? log.planDayTitle : log.freeTextLabel) ?? '';
  }

  protected readonly canSave = computed(
    () => this._name().trim().length > 0 && this._dayTitles().every((t) => t.trim().length > 0) && !this._saving(),
  );

  protected setName(value: string): void {
    this._name.set(value);
  }

  protected setDayTitle(index: number, value: string): void {
    const titles = [...this._dayTitles()];
    titles[index] = value;
    this._dayTitles.set(titles);
  }

  protected addDay(): void {
    this._dayTitles.set([...this._dayTitles(), '']);
  }

  protected removeDay(index: number): void {
    const titles = this._dayTitles().filter((_, i) => i !== index);
    this._dayTitles.set(titles.length > 0 ? titles : ['']);
  }

  protected confirm(): void {
    if (!this.canSave()) {
      return;
    }
    this.saved.emit({
      name: this._name().trim(),
      dayTitles: this._dayTitles().map((t) => t.trim()),
    });
  }

  protected cancel(): void {
    this.dismiss.emit();
  }

  protected formatClassDate(value: string | null): string {
    if (!value) {
      return '';
    }
    const [y, m, d] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('es-CL', { day: 'numeric', month: 'long' }).format(new Date(y, m - 1, d));
  }
}
