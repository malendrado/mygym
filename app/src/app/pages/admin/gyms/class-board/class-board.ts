import { Component, DestroyRef, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { IonButton, IonContent, IonIcon, IonModal, IonSpinner, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  addCircleOutline,
  alertCircleOutline,
  barbellOutline,
  checkmarkCircleOutline,
  chevronForwardOutline,
  closeCircleOutline,
  informationCircleOutline,
  logoWhatsapp,
} from 'ionicons/icons';
import { BoardAttendee, BoardClass, ClassBoard } from '../../../../core/models/class-board.model';
import { ExerciseLogEntry, MemberWorkoutLog, WorkoutPlan } from '../../../../core/models/workout.model';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { ClassBoardService } from '../../../../core/services/class-board.service';
import { WorkoutService } from '../../../../core/services/workout.service';
import { santiagoNow } from '../../../../core/utils/attendance';
import {
  BoardFilter,
  boardCounts,
  deviationText,
  exercisesLine,
  filterAttendees,
  sortAttendees,
} from '../../../../core/utils/class-board';
import { formatIsoDate } from '../../../../core/utils/date-format';
import { errorText } from '../../../../core/utils/error-text';
import { whatsappUrl } from '../../../../core/utils/member-profile';
import { shortTime } from '../../../../core/utils/time-format';

addIcons({
  'add-circle-outline': addCircleOutline,
  'alert-circle-outline': alertCircleOutline,
  'barbell-outline': barbellOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'close-circle-outline': closeCircleOutline,
  'information-circle-outline': informationCircleOutline,
  'logo-whatsapp': logoWhatsapp,
});

const REFRESH_MS = 60_000;
const MAX_EXERCISE_ROWS = 10;

/**
 * Tablero "En clase" (subsección de Socios): las clases en curso (todas) y la siguiente, y por alumno qué le toca
 * hoy y qué hizo la última vez, para que el profesor llegue a la clase sabiendo qué decirle a cada uno. Al tocar una
 * tarjeta se abre el detalle (plan, últimos registros, WhatsApp) y desde ahí el profesor puede anotar la rutina por
 * el alumno. Se recarga solo cada minuto y al volver a la pestaña. Compartido por gym-admin y gym-form.
 * Ver docs/plans/2026-10-12-en-clase-memoria-viva.md.
 */
@Component({
  selector: 'app-class-board',
  imports: [IonButton, IonContent, IonIcon, IonModal, IonSpinner],
  templateUrl: './class-board.html',
  styleUrl: './class-board.scss',
})
export class ClassBoardView {
  private readonly boardService = inject(ClassBoardService);
  private readonly workoutService = inject(WorkoutService);
  private readonly attendanceService = inject(AttendanceService);
  private readonly toastController = inject(ToastController);

  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  @Input() demo = false;
  /** El padre lo sube cuando cambió una rutina (para que "Sin rutina" se actualice al instante). */
  @Input() set refreshToken(value: number) {
    if (value !== this.lastToken) {
      this.lastToken = value;
      this.load(true);
    }
  }
  /** Pide abrir el editor de rutina del socio (el modal vive en el panel). */
  @Output() readonly editRoutine = new EventEmitter<{ memberId: number; name: string }>();

  private lastToken = 0;

  protected readonly board = signal<ClassBoard | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly chosenKey = signal<string | null>(null);
  protected readonly filter = signal<BoardFilter>('all');

  // ---- detalle de un alumno ----
  protected readonly detailMemberId = signal<number | null>(null);
  protected readonly plan = signal<WorkoutPlan | null>(null);
  protected readonly planLoading = signal(false);
  protected readonly history = signal<MemberWorkoutLog[]>([]);
  protected readonly marking = signal(false);

  // ---- formulario "Anotar por él" ----
  protected readonly formOpen = signal(false);
  protected readonly formDayId = signal<number | null>(null);
  protected readonly formFree = signal(false);
  protected readonly formFreeText = signal('');
  protected readonly formRows = signal<ExerciseLogEntry[]>([{ exercise: '', notes: '' }]);
  protected readonly saving = signal(false);

  constructor() {
    this.load(false);
    // Con la pestaña oculta no se consulta (el navegador la deja en segundo plano y nadie lo está mirando): al volver
    // a verla se recarga sola (onVisible).
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        this.load(true);
      }
    }, REFRESH_MS);
    const onVisible = (): void => {
      if (document.visibilityState === 'visible') {
        this.load(true);
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    });
  }

  // ---- carga ----

  protected retry(): void {
    this.load(false);
  }

  private load(silent: boolean): void {
    if (!silent) {
      this.loading.set(true);
    }
    this.failed.set(false);
    this.boardService.get(this.gymId).subscribe({
      next: (board) => {
        this.board.set(board);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        // Una recarga silenciosa que falla no tapa lo que ya se ve.
        if (!this.board()) {
          this.failed.set(true);
        }
      },
    });
  }

  // ---- clases y selección ----

  protected readonly classes = computed(() => this.board()?.classes ?? []);

  protected classKey(c: BoardClass): string {
    return `${c.blockId}|${c.classDate}`;
  }

  // Sin elección manual: la primera en curso; si no hay, la siguiente.
  protected readonly selected = computed<BoardClass | null>(() => {
    const list = this.classes();
    const chosen = this.chosenKey();
    return (
      list.find((c) => this.classKey(c) === chosen) ?? list.find((c) => c.status === 'LIVE') ?? list[0] ?? null
    );
  });

  protected choose(c: BoardClass): void {
    this.chosenKey.set(this.classKey(c));
    this.filter.set('all');
  }

  protected pillLabel(c: BoardClass): string {
    const when = c.status === 'LIVE' ? 'En curso' : 'Siguiente';
    const day = this.dayWord(c.classDate);
    return `${when} · ${day ? day + ' ' : ''}${shortTime(c.startTime)} ${c.label}`;
  }

  /** "" si es hoy; "mañana"; o la fecha. */
  protected dayWord(date: string): string {
    const today = santiagoNow().date;
    if (date === today) {
      return '';
    }
    const [y, m, d] = today.split('-').map(Number);
    const tomorrow = new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
    return date === tomorrow ? 'mañana' : formatIsoDate(date);
  }

  protected readonly counts = computed(() => boardCounts(this.selected()?.attendees ?? []));
  protected readonly visible = computed(() =>
    sortAttendees(filterAttendees(this.selected()?.attendees ?? [], this.filter())),
  );

  protected setFilter(f: BoardFilter): void {
    this.filter.set(this.filter() === f ? 'all' : f);
  }

  // ---- tarjeta ----

  protected shortTime(t: string): string {
    return shortTime(t);
  }
  protected whatsapp(a: BoardAttendee): string | null {
    return whatsappUrl(a.phone);
  }
  protected deviation(a: BoardAttendee): string | null {
    return deviationText(a.lastLog);
  }
  protected exercises(a: BoardAttendee): string {
    return exercisesLine(a.lastLog?.exercises);
  }
  protected lastDate(a: BoardAttendee): string {
    return a.lastLog ? this.shortDate(a.lastLog.classDate) : '';
  }
  protected shortDate(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('es-CL', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(y, m - 1, d));
  }
  protected arrivedText(a: BoardAttendee): string {
    if (!a.checkedInAt) {
      return 'No ha llegado';
    }
    const time = new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(a.checkedInAt));
    return `Llegó ${time}${a.checkinMethod === 'MANUAL' ? ' · a mano' : ''}`;
  }

  // ---- detalle ----

  protected readonly detail = computed<BoardAttendee | null>(() => {
    const id = this.detailMemberId();
    return id === null ? null : (this.selected()?.attendees.find((a) => a.memberId === id) ?? null);
  });

  protected openDetail(a: BoardAttendee): void {
    this.detailMemberId.set(a.memberId);
    this.formOpen.set(false);
    this.plan.set(null);
    this.history.set([]);
    this.planLoading.set(true);
    this.workoutService.getActivePlan(a.memberId, this.gymId).subscribe({
      next: (plan) => {
        this.plan.set(plan);
        this.planLoading.set(false);
      },
      error: () => this.planLoading.set(false),
    });
    this.workoutService.getLogHistory(a.memberId, this.gymId).subscribe({
      next: (logs) => this.history.set(logs.slice(0, 5)),
      error: () => undefined,
    });
  }

  protected closeDetail(): void {
    this.detailMemberId.set(null);
    this.formOpen.set(false);
  }

  protected emitEditRoutine(a: BoardAttendee): void {
    this.closeDetail();
    this.editRoutine.emit({ memberId: a.memberId, name: a.name });
  }

  protected markArrived(a: BoardAttendee): void {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    this.marking.set(true);
    this.attendanceService.setAttendance(a.reservationId, true, this.gymId).subscribe({
      next: (mark) => {
        this.marking.set(false);
        this.patchAttendee(a.memberId, { checkedInAt: mark.checkedInAt, checkinMethod: mark.checkinMethod });
        this.toast(`${a.name}: marcado como que llegó.`);
      },
      error: (err) => {
        this.marking.set(false);
        this.toast(errorText(err, 'No pudimos marcar la asistencia. Intenta nuevamente.'), 'danger');
      },
    });
  }

  private patchAttendee(memberId: number, patch: Partial<BoardAttendee>): void {
    this.board.update((b) =>
      b
        ? {
            ...b,
            classes: b.classes.map((c) => ({
              ...c,
              attendees: c.attendees.map((a) => (a.memberId === memberId ? { ...a, ...patch } : a)),
            })),
          }
        : b,
    );
  }

  // ---- anotar por él ----

  protected openForm(a: BoardAttendee): void {
    const own = a.thisClassLog;
    this.formFree.set(own ? own.planDayId === null : false);
    this.formFreeText.set(own?.freeTextLabel ?? '');
    this.formDayId.set(own ? own.planDayId : (a.nextDay?.id ?? null));
    this.formRows.set(own?.exercises?.length ? own.exercises.map((e) => ({ exercise: e.exercise, notes: e.notes ?? '' })) : [{ exercise: '', notes: '' }]);
    this.formOpen.set(true);
  }

  protected pickDay(id: number): void {
    this.formFree.set(false);
    this.formDayId.set(id);
  }

  protected pickFree(): void {
    this.formFree.set(true);
    this.formDayId.set(null);
  }

  protected setRow(index: number, field: 'exercise' | 'notes', value: string): void {
    this.formRows.update((rows) => rows.map((r, i) => (i === index ? { ...r, [field]: value } : r)));
  }

  protected addRow(): void {
    if (this.formRows().length < MAX_EXERCISE_ROWS) {
      this.formRows.update((rows) => [...rows, { exercise: '', notes: '' }]);
    }
  }

  protected removeRow(index: number): void {
    this.formRows.update((rows) => (rows.length > 1 ? rows.filter((_, i) => i !== index) : rows));
  }

  protected readonly canSave = computed(() =>
    this.formFree() ? this.formFreeText().trim().length > 0 : this.formDayId() !== null,
  );

  protected save(a: BoardAttendee): void {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    if (!this.canSave() || this.saving()) {
      return;
    }
    const payload = {
      planDayId: this.formFree() ? null : this.formDayId(),
      freeTextLabel: this.formFree() ? this.formFreeText().trim() : null,
      exercises: this.formRows()
        .filter((r) => r.exercise.trim())
        .map((r) => ({ exercise: r.exercise.trim(), notes: r.notes?.trim() ? r.notes.trim() : null })),
    };
    this.saving.set(true);
    const own = a.thisClassLog;
    const request = own
      ? this.workoutService.updateLogForMember(a.memberId, own.id, payload, this.gymId)
      : this.workoutService.createLogForMember(a.memberId, a.reservationId, payload, this.gymId);
    request.subscribe({
      next: (log) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.patchAttendee(a.memberId, { thisClassLog: log, unloggedLast: a.unloggedLast });
        this.toast(`Rutina de ${a.name} anotada.`);
        this.load(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.toast(errorText(err, 'No pudimos guardar el registro. Intenta nuevamente.'), 'danger');
      },
    });
  }

  protected deviatedLog(log: MemberWorkoutLog): boolean {
    return log.planDayId === null || log.planDayId !== log.suggestedPlanDayId;
  }

  protected doneLabel(log: MemberWorkoutLog): string {
    return (log.planDayId !== null ? log.planDayTitle : log.freeTextLabel) ?? '';
  }

  protected exercisesOf(log: MemberWorkoutLog): string {
    return exercisesLine(log.exercises);
  }

  private async toast(message: string, color: 'success' | 'warning' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 2500, position: 'bottom', color });
    await toast.present();
  }
}
