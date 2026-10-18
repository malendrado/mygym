import {
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { IonIcon, IonSpinner, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  arrowUndoOutline,
  calendarOutline,
  checkmarkCircleOutline,
  checkmarkDoneOutline,
  chevronBackOutline,
  chevronDownOutline,
  chevronForwardOutline,
  closeCircleOutline,
  logoWhatsapp,
  personRemoveOutline,
} from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { Attendee } from '../../../../core/models/member.model';
import { AttendanceMark } from '../../../../core/models/attendance.model';
import { AgendaOccurrence, AgendaWaitlistEntry, GymBlock } from '../../../../core/models/gym.model';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { GymService } from '../../../../core/services/gym.service';
import {
  AttendanceCounts,
  addDays,
  attendanceEditable,
  attendanceOf,
  capacityState,
  mondayOf,
  occurrenceEnded,
  occurrenceStarted,
  periodOf,
  ratePct,
  rateTone,
  santiagoNow,
  sumCounts,
} from '../../../../core/utils/attendance';
import { errorText } from '../../../../core/utils/error-text';
import { whatsappUrl } from '../../../../core/utils/member-profile';
import { missedClassMessage, waitlistSpotMessage } from '../../../../core/utils/whatsapp-messages';
import { TIME_BAND_ALL_HINT, TIME_BAND_OPTIONS, TimeBand, timeBandOf } from '../../../../core/utils/time-band';

addIcons({
  'arrow-undo-outline': arrowUndoOutline,
  'calendar-outline': calendarOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'checkmark-done-outline': checkmarkDoneOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-down-outline': chevronDownOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'close-circle-outline': closeCircleOutline,
  'logo-whatsapp': logoWhatsapp,
  'person-remove-outline': personRemoveOutline,
});

// Cuántas marcas de asistencia viajan a la vez en "Marcar a todos".
const MARK_CONCURRENCY = 5;

const DOW_BY_INDEX = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'] as const;
const WEEKDAY_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

// Se crea una sola vez: el resumen de la semana recalcula la hora de cada asistente en cada cambio.
const TIME_FORMAT = new Intl.DateTimeFormat('es-CL', {
  timeZone: 'America/Santiago',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function dayIndexOf(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).getDay();
}

interface DayChip {
  date: string;
  weekday: string;
  dayNumber: number;
  isToday: boolean;
  hasClasses: boolean;
}

interface AttendeeRow {
  attendee: Attendee;
  attended: boolean;
  /** "19:04 · QR" / "19:04 · a mano"; "" si no marcó. */
  stamp: string;
  whatsapp: string | null;
}

interface WaitlistRow {
  entry: AgendaWaitlistEntry;
  /** Hora corta (HH:MM) en que se anotó. */
  since: string;
  whatsapp: string | null;
}

interface ClassRow {
  block: GymBlock;
  attendees: AttendeeRow[];
  counts: AttendanceCounts;
  ended: boolean;
  started: boolean;
  rate: number | null;
  editable: boolean;
  /** Reservas aún sin marcar como que vinieron (para "Marcar a todos"). */
  unmarked: number;
  /** Pasaron más de 7 días: solo lectura. */
  tooOld: boolean;
  capacity: number;
  /** Reservas vigentes (BOOKED). */
  taken: number;
  /** 0–100, para la barra de cupos. */
  fillPct: number;
  full: boolean;
  closed: boolean;
  closedReason: string | null;
  waitlistCount: number;
  /** Quiénes esperan; vacío si el servidor no los manda (entonces solo se ve el conteo). */
  waitlist: WaitlistRow[];
  /** false mientras la agenda de la semana aún no llega: no se pinta "0/8 · Sin reservas" con datos que no son. */
  dataReady: boolean;
}

/**
 * Agenda del gimnasio (sub-pestaña de Clases): una semana a la vez (tira de días) y, del día elegido, cada clase
 * con sus cupos ("8/12"), si está llena / cerrada / con gente en espera, y la lista de quienes reservaron SIEMPRE
 * visible (sin acordeón). En clases pasadas o en curso cada persona muestra ✓ vino / ✗ no vino (y cómo marcó) con el
 * botón para corregirlo. El consolidado semana/mes vive en la sub-pestaña Asistencia (AttendanceTab): la línea del
 * final solo lo anuncia y emite `openAttendance`.
 *
 * Compartido por gym-admin y gym-form (`gymId` null = gym del JWT). Pide la semana completa en UNA llamada (agenda,
 * con clases sin reservas incluidas) y la guarda por rango; el padre solo le pasa los bloques y atiende "cancelar
 * reserva" (que pide confirmación y habla con su propio servicio), avisando por `refreshToken` cuando termina.
 */
@Component({
  selector: 'app-history-board',
  imports: [IonIcon, IonSpinner],
  templateUrl: './history-board.html',
  styleUrl: './history-board.scss',
})
export class HistoryBoard {
  private readonly gymService = inject(GymService);
  private readonly attendanceService = inject(AttendanceService);
  private readonly toastController = inject(ToastController);

  @Input({ required: true }) set blocks(value: GymBlock[]) {
    this.blocksSig.set(value);
  }
  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  @Input() demo = false;
  /** El padre lo sube cuando cambió algo por su lado (cancelar una reserva): se vuelve a pedir la semana. */
  @Input() set refreshToken(value: number) {
    this.tokenSig.set(value);
  }
  @Output() readonly cancelRequested = new EventEmitter<{ attendee: Attendee; blockId: number; date: string }>();
  /** "Ver asistencia": el padre cambia a la sub-pestaña Asistencia, abierta en la semana que se estaba mirando. */
  @Output() readonly openAttendance = new EventEmitter<string>();

  private readonly blocksSig = signal<GymBlock[]>([]);
  private readonly tokenSig = signal(0);
  private lastToken = 0;

  protected readonly today = santiagoNow().date;
  private readonly anchor = signal(this.today);
  protected readonly selectedDate = signal(this.today);
  // Franja horaria: abre en la de AHORA (igual que el filtro de Horarios); "Todas" = null.
  protected readonly selectedBand = signal<TimeBand | null>(timeBandOf(santiagoNow().time));
  protected readonly bandOptions = TIME_BAND_OPTIONS;
  protected readonly bandAllHint = TIME_BAND_ALL_HINT;
  protected readonly loading = signal(false);
  private readonly occurrencesByKey = signal<Record<string, AgendaOccurrence>>({});
  private readonly loadedRanges = new Set<string>();
  // Cambios optimistas: reservationId → true/false mientras el servidor responde.
  private readonly pending = signal<Record<number, boolean>>({});

  protected readonly weekStart = computed(() => mondayOf(this.anchor()));
  protected readonly weekDates = computed(() => Array.from({ length: 7 }, (_, i) => addDays(this.weekStart(), i)));
  protected readonly weekLabel = computed(() => periodOf(this.anchor(), 'week').label);
  protected readonly isCurrentWeek = computed(() => this.weekStart() === mondayOf(this.today));
  protected readonly showTodayButton = computed(() => this.selectedDate() !== this.today);

  constructor() {
    // Cada semana nueva (o un aviso del padre) pide la agenda — una llamada por rango, con caché.
    effect(() => {
      const start = this.weekStart();
      const token = this.tokenSig();
      untracked(() => {
        if (token !== this.lastToken) {
          this.lastToken = token;
          this.loadedRanges.clear();
        }
        this.loadWeek(start);
      });
    });
  }

  // ---- datos ----

  private key(blockId: number, date: string): string {
    return `${blockId}_${date}`;
  }

  private loadWeek(start: string): void {
    const from = start;
    const to = addDays(start, 6);
    const rangeKey = `${from}_${to}`;
    if (this.loadedRanges.has(rangeKey)) {
      return;
    }
    this.loadedRanges.add(rangeKey);
    this.loading.set(true);
    const request =
      this.gymId === null ? this.gymService.getMyAgenda(from, to) : this.gymService.getAgenda(this.gymId, from, to);
    request.subscribe({
      next: (occurrences) => {
        this.occurrencesByKey.update((map) => {
          const next = { ...map };
          // La respuesta es la verdad de la semana: se reemplaza lo que había de esos 7 días.
          for (const date of this.weekDatesOf(start)) {
            for (const block of this.blocksSig()) {
              delete next[this.key(block.id, date)];
            }
          }
          for (const occurrence of occurrences) {
            next[this.key(occurrence.gymBlockId, occurrence.classDate)] = occurrence;
          }
          return next;
        });
        this.loading.set(false);
      },
      error: () => {
        // Se permite reintentar al volver a esa semana.
        this.loadedRanges.delete(rangeKey);
        this.loading.set(false);
      },
    });
  }

  private weekDatesOf(start: string): string[] {
    return Array.from({ length: 7 }, (_, i) => addDays(start, i));
  }

  private occurrenceOf(blockId: number, date: string): AgendaOccurrence | undefined {
    return this.occurrencesByKey()[this.key(blockId, date)];
  }

  private attendeesOf(blockId: number, date: string): Attendee[] {
    return this.occurrenceOf(blockId, date)?.attendees ?? [];
  }

  /** Clases de un día: las activas y las inactivas que igual tuvieron reservas ese día. */
  private blocksOn(date: string): GymBlock[] {
    const dow = DOW_BY_INDEX[dayIndexOf(date)];
    return this.blocksSig()
      .filter((b) => b.dayOfWeek === dow && (b.active || this.attendeesOf(b.id, date).length > 0))
      .sort((a, b) => a.startTime.localeCompare(b.startTime));
  }

  // ---- tira de días y clases del día ----

  protected readonly days = computed<DayChip[]>(() => {
    this.occurrencesByKey();
    return this.weekDates().map((date) => ({
      date,
      weekday: WEEKDAY_SHORT[dayIndexOf(date)],
      dayNumber: Number(date.slice(8, 10)),
      isToday: date === this.today,
      hasClasses: this.blocksOn(date).length > 0,
    }));
  });

  protected readonly dayHeading = computed(() => {
    const [y, m, d] = this.selectedDate().split('-').map(Number);
    const label = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long' }).format(
      new Date(y, m - 1, d),
    );
    return label.charAt(0).toUpperCase() + label.slice(1);
  });

  private effectiveAttended(a: Attendee): boolean {
    return this.pending()[a.reservationId] ?? a.checkedInAt !== null;
  }

  private stampOf(a: Attendee): string {
    if (this.pending()[a.reservationId] !== undefined && a.checkedInAt === null) {
      return 'ahora · a mano';
    }
    if (!a.checkedInAt) {
      return '';
    }
    const time = TIME_FORMAT.format(new Date(a.checkedInAt));
    return `${time} · ${a.checkinMethod === 'MANUAL' ? 'a mano' : 'QR'}`;
  }

  private rowsFor(date: string): ClassRow[] {
    const now = new Date();
    return this.blocksOn(date).map((block) => {
      const occurrence = this.occurrenceOf(block.id, date);
      const ended = occurrenceEnded(date, block.endTime, now);
      const started = occurrenceStarted(date, block.startTime, now);
      const attendees = this.attendeesOf(block.id, date).map<AttendeeRow>((attendee) => {
        const attended = this.effectiveAttended(attendee);
        return { attendee, attended, stamp: attended ? this.stampOf(attendee) : '', whatsapp: whatsappUrl(attendee.phone, missedClassMessage(attendee.name, block.label)) };
      });
      // Lo accionable primero: los que no vinieron (o siguen sin marcar), después los que vinieron.
      attendees.sort((a, b) => Number(a.attended) - Number(b.attended) || a.attendee.name.localeCompare(b.attendee.name));
      const counts = attendanceOf(
        attendees.map((a) => ({ checkedInAt: a.attended ? 'x' : null })),
        ended,
      );
      const editable = attendanceEditable(date, block.startTime, block.endTime, now);
      // Mientras la agenda no llega, los cupos salen del bloque y de lo ya cargado; al llegar manda el servidor.
      const capacity = occurrence?.capacity ?? block.capacity;
      const taken = occurrence?.taken ?? attendees.length;
      const cupos = capacityState(taken, capacity);
      return {
        block,
        attendees,
        counts,
        ended,
        started,
        rate: ended ? ratePct(counts.attended, counts.booked) : null,
        editable,
        unmarked: counts.booked - counts.attended,
        tooOld: started && !editable,
        capacity,
        taken,
        fillPct: cupos.fillPct,
        full: cupos.full,
        closed: occurrence?.closed ?? false,
        closedReason: occurrence?.closedReason ?? null,
        waitlistCount: occurrence?.waitlistCount ?? 0,
        waitlist: (occurrence?.waitlist ?? []).map<WaitlistRow>((entry) => ({
          entry,
          since: this.sinceOf(entry.joinedAt),
          whatsapp: whatsappUrl(
            entry.phone,
            waitlistSpotMessage(entry.name, this.classLabel(block), date, block.startTime.slice(0, 5)),
          ),
        })),
        dataReady: occurrence !== undefined || !this.loading(),
      };
    });
  }

  protected readonly classRows = computed<ClassRow[]>(() => {
    this.occurrencesByKey();
    this.pending();
    this.loading();
    const band = this.selectedBand();
    return this.rowsFor(this.selectedDate()).filter((row) => !band || timeBandOf(row.block.startTime) === band);
  });

  // Cuántas clases tiene el día elegido en cada franja (independiente de la franja elegida, como los contadores de Horarios).
  protected bandCount(band: TimeBand | null): number {
    this.occurrencesByKey();
    return this.blocksOn(this.selectedDate()).filter((b) => !band || timeBandOf(b.startTime) === band).length;
  }

  protected readonly emptyMessage = computed(() => {
    const band = this.selectedBand();
    if (band && this.blocksOn(this.selectedDate()).length > 0) {
      const label = this.bandOptions.find((b) => b.value === band)?.label.toLowerCase();
      return `No hay clases de ${label} este día. Elige «Todas» para ver el resto.`;
    }
    return 'No hay clases este día.';
  });

  // Total de la semana (solo clases terminadas): una línea de resumen sin pedir nada más al servidor.
  protected readonly weekTotals = computed<{ counts: AttendanceCounts; rate: number | null }>(() => {
    this.occurrencesByKey();
    this.pending();
    const counts = sumCounts(
      this.weekDates()
        .flatMap((date) => this.rowsFor(date))
        .filter((r) => r.ended)
        .map((r) => r.counts),
    );
    return { counts, rate: ratePct(counts.attended, counts.booked) };
  });
  protected readonly weekTone = computed(() => {
    const rate = this.weekTotals().rate;
    return rate === null ? 'none' : rateTone(rate);
  });

  protected tone(rate: number | null): string {
    return rate === null ? 'none' : rateTone(rate);
  }

  private classLabel(block: GymBlock): string {
    const time = block.startTime.slice(0, 5);
    return block.label.replace(time, '').replace(/[\s\-–·]+$/, '').trim() || block.label;
  }

  private sinceOf(joinedAt: string): string {
    const when = new Date(joinedAt);
    return Number.isNaN(when.getTime()) ? '' : TIME_FORMAT.format(when);
  }

  // Listas de espera desplegadas, por id de bloque.
  protected readonly openWaitlists = signal<Record<number, boolean>>({});

  protected toggleWaitlist(blockId: number): void {
    this.openWaitlists.update((open) => ({ ...open, [blockId]: !open[blockId] }));
  }

  protected blockTitle(row: ClassRow): string {
    const time = row.block.startTime.slice(0, 5);
    // Hay gimnasios que ya escriben la hora en el nombre ("Funcional 13:00"): se saca de ahí para no mostrarla dos veces.
    const label = row.block.label.replace(time, '').replace(/[\s\-–·]+$/, '').trim() || row.block.label;
    return `${time} · ${label}`;
  }

  protected cupoLabel(row: ClassRow): string {
    return `${row.taken} de ${row.capacity} cupos ocupados`;
  }

  /** Se puede cancelar la reserva de un admin solo en clases que no pasaron (mismo criterio del backend). */
  protected canCancel(): boolean {
    return this.selectedDate() >= this.today;
  }

  // ---- navegación ----

  protected selectBand(band: TimeBand | null): void {
    this.selectedBand.set(band);
  }

  protected selectDay(date: string): void {
    this.selectedDate.set(date);
  }

  protected goToday(): void {
    this.anchor.set(this.today);
    this.selectDay(this.today);
  }

  protected shiftWeek(direction: -1 | 1): void {
    const newStart = addDays(this.weekStart(), direction * 7);
    this.anchor.set(newStart);
    // Misma posición de la semana (o hoy, si la semana nueva es la actual).
    const offset = (dayIndexOf(this.selectedDate()) + 6) % 7;
    this.selectDay(mondayOf(this.today) === newStart ? this.today : addDays(newStart, offset));
  }

  protected pickDate(input: HTMLInputElement): void {
    if (/^\d{4}-\d{2}-\d{2}$/.test(input.value)) {
      this.anchor.set(input.value);
      this.selectDay(input.value);
    }
  }

  protected openPicker(input: HTMLInputElement): void {
    if (typeof input.showPicker === 'function') {
      input.showPicker();
    } else {
      input.focus();
      input.click();
    }
  }

  protected goToAttendance(): void {
    this.openAttendance.emit(this.anchor());
  }

  // ---- marcar asistencia ----

  protected toggle(row: ClassRow, item: AttendeeRow): void {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    if (!row.editable) {
      return;
    }
    const id = item.attendee.reservationId;
    const target = !item.attended;
    const date = this.selectedDate();
    this.pending.update((p) => ({ ...p, [id]: target }));
    this.attendanceService.setAttendance(id, target, this.gymId).subscribe({
      next: (mark) => {
        this.applyMark(this.key(row.block.id, date), mark);
        this.dropPending(id);
        this.toast(target ? `${item.attendee.name}: marcado como que vino.` : `${item.attendee.name}: marcado como que no vino.`);
      },
      error: (err) => {
        this.dropPending(id);
        this.toast(errorText(err, 'No pudimos guardar el cambio. Intenta nuevamente.'), 'danger');
      },
    });
  }

  private applyMark(key: string, mark: AttendanceMark): void {
    this.occurrencesByKey.update((map) => {
      const current = map[key];
      if (!current) {
        return map;
      }
      return {
        ...map,
        [key]: {
          ...current,
          attendees: current.attendees.map((a) =>
            a.reservationId === mark.reservationId
              ? { ...a, checkedInAt: mark.checkedInAt, checkinMethod: mark.checkinMethod }
              : a,
          ),
        },
      };
    });
  }

  /**
   * Marca (o desmarca) a varias reservas con las llamadas por persona de siempre (no hay endpoint en lote), de a
   * MARK_CONCURRENCY. Cada resultado se aplica apenas llega; lo que falla vuelve a su estado real. Devuelve los ids que sí se guardaron.
   */
  private async markMany(key: string, ids: number[], target: boolean): Promise<number[]> {
    this.pending.update((p) => ({ ...p, ...Object.fromEntries(ids.map((id) => [id, target])) }));
    const done: number[] = [];
    const queue = [...ids];
    const worker = async (): Promise<void> => {
      for (let id = queue.shift(); id !== undefined; id = queue.shift()) {
        try {
          const mark = await firstValueFrom(this.attendanceService.setAttendance(id, target, this.gymId));
          this.applyMark(key, mark);
          done.push(id);
        } catch {
          // queda fuera de `done`: abajo vuelve a su estado real
        }
        this.dropPending(id);
      }
    };
    await Promise.all(Array.from({ length: Math.min(MARK_CONCURRENCY, ids.length) }, worker));
    return done;
  }

  protected async markAll(row: ClassRow): Promise<void> {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    if (!row.editable || row.closed) {
      return;
    }
    const key = this.key(row.block.id, this.selectedDate());
    const ids = row.attendees.filter((a) => !a.attended).map((a) => a.attendee.reservationId);
    if (ids.length === 0) {
      return;
    }
    const done = await this.markMany(key, ids, true);
    if (done.length < ids.length) {
      this.toast(
        done.length === 0
          ? 'No pudimos guardar la asistencia. Intenta nuevamente.'
          : `Marcaste a ${done.length} de ${ids.length}. Reintenta.`,
        done.length === 0 ? 'danger' : 'warning',
      );
      return;
    }
    const toast = await this.toastController.create({
      message: `Marcaste a ${done.length}`,
      duration: 6000,
      position: 'bottom',
      color: 'success',
      buttons: [{ text: 'Deshacer', handler: () => void this.undoMarkAll(key, done) }],
    });
    await toast.present();
  }

  /** Deshace solo lo que marcó "Marcar a todos", no lo que ya estaba marcado de antes. */
  private async undoMarkAll(key: string, ids: number[]): Promise<void> {
    const undone = await this.markMany(key, ids, false);
    if (undone.length < ids.length) {
      this.toast(`Deshiciste a ${undone.length} de ${ids.length}. Reintenta.`, 'warning');
    } else {
      this.toast(`Deshiciste la asistencia de ${undone.length}.`);
    }
  }

  protected requestCancel(row: ClassRow, item: AttendeeRow): void {
    this.cancelRequested.emit({ attendee: item.attendee, blockId: row.block.id, date: this.selectedDate() });
  }

  private dropPending(id: number): void {
    this.pending.update((p) => {
      const { [id]: _removed, ...rest } = p;
      return rest;
    });
  }

  private async toast(message: string, color: 'success' | 'warning' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 2500, position: 'bottom', color });
    await toast.present();
  }
}
