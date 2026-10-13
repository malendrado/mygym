import { Component, EventEmitter, Input, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { IonButton, IonIcon, IonSpinner, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  checkmarkCircleOutline,
  chevronBackOutline,
  chevronDownOutline,
  chevronForwardOutline,
  chevronUpOutline,
  closeCircleOutline,
  logoWhatsapp,
} from 'ionicons/icons';
import { Attendee } from '../../../../core/models/member.model';
import { GymBlock } from '../../../../core/models/gym.model';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { GymService } from '../../../../core/services/gym.service';
import {
  AttendanceCounts,
  PeriodMode,
  addDays,
  attendanceEditable,
  attendanceOf,
  mondayOf,
  occurrenceEnded,
  occurrenceStarted,
  periodOf,
  ratePct,
  rateTone,
  santiagoNow,
  shiftAnchor,
  sumCounts,
} from '../../../../core/utils/attendance';
import { errorText } from '../../../../core/utils/error-text';
import { whatsappUrl } from '../../../../core/utils/member-profile';
import { AttendanceSummaryView } from '../attendance-summary/attendance-summary';

addIcons({
  'calendar-outline': calendarOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-down-outline': chevronDownOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'chevron-up-outline': chevronUpOutline,
  'close-circle-outline': closeCircleOutline,
  'logo-whatsapp': logoWhatsapp,
});

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

interface ClassRow {
  block: GymBlock;
  attendees: AttendeeRow[];
  counts: AttendanceCounts;
  ended: boolean;
  started: boolean;
  rate: number | null;
  editable: boolean;
  /** Pasaron más de 7 días: solo lectura. */
  tooOld: boolean;
}

/**
 * Historial del gimnasio: primero la clase, el resumen después. Una semana a la vez (tira de días), las clases
 * del día elegido en lista y, al tocar una, quién reservó con ✓ vino / ✗ no vino (y cómo marcó) con el botón para
 * corregirlo. "Ver resumen" abre el resumen de semana o mes (AttendanceSummaryView).
 *
 * Compartido por gym-admin y gym-form (`gymId` null = gym del JWT). Pide la semana completa en UNA llamada
 * (history-attendees) y la guarda por rango; el padre solo le pasa los bloques y atiende "cancelar reserva"
 * (que pide confirmación y habla con su propio servicio), avisando por `refreshToken` cuando termina.
 */
@Component({
  selector: 'app-history-board',
  imports: [IonButton, IonIcon, IonSpinner, AttendanceSummaryView],
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

  private readonly blocksSig = signal<GymBlock[]>([]);
  private readonly tokenSig = signal(0);
  private lastToken = 0;

  protected readonly today = santiagoNow().date;
  protected readonly view = signal<'day' | 'summary'>('day');
  private readonly anchor = signal(this.today);
  protected readonly selectedDate = signal(this.today);
  protected readonly expandedBlockId = signal<number | null>(null);
  protected readonly loading = signal(false);
  private readonly attendeesByKey = signal<Record<string, Attendee[]>>({});
  private readonly loadedRanges = new Set<string>();
  // Cambios optimistas: reservationId → true/false mientras el servidor responde.
  private readonly pending = signal<Record<number, boolean>>({});

  // Resumen (semana o mes)
  protected readonly summaryMode = signal<PeriodMode>('week');
  private readonly summaryAnchor = signal(this.today);
  protected readonly summaryPeriod = computed(() => periodOf(this.summaryAnchor(), this.summaryMode()));

  protected readonly weekStart = computed(() => mondayOf(this.anchor()));
  protected readonly weekDates = computed(() => Array.from({ length: 7 }, (_, i) => addDays(this.weekStart(), i)));
  protected readonly weekLabel = computed(() => periodOf(this.anchor(), 'week').label);
  protected readonly isCurrentWeek = computed(() => this.weekStart() === mondayOf(this.today));
  protected readonly showTodayButton = computed(() => this.selectedDate() !== this.today);

  constructor() {
    // Cada semana nueva (o un aviso del padre) pide los asistentes — una llamada por rango, con caché.
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
      this.gymId === null
        ? this.gymService.getMyHistoryAttendees(from, to)
        : this.gymService.getHistoryAttendees(this.gymId, from, to);
    request.subscribe({
      next: (occurrences) => {
        this.attendeesByKey.update((map) => {
          const next = { ...map };
          // El batch solo trae clases CON reservas: las de la semana que no vengan quedan vacías.
          for (const date of this.weekDatesOf(start)) {
            for (const block of this.blocksSig()) {
              delete next[this.key(block.id, date)];
            }
          }
          for (const occurrence of occurrences) {
            next[this.key(occurrence.gymBlockId, occurrence.classDate)] = occurrence.attendees;
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

  private attendeesOf(blockId: number, date: string): Attendee[] {
    return this.attendeesByKey()[this.key(blockId, date)] ?? [];
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
    this.attendeesByKey();
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
      const ended = occurrenceEnded(date, block.endTime, now);
      const started = occurrenceStarted(date, block.startTime, now);
      const attendees = this.attendeesOf(block.id, date).map<AttendeeRow>((attendee) => {
        const attended = this.effectiveAttended(attendee);
        return { attendee, attended, stamp: attended ? this.stampOf(attendee) : '', whatsapp: whatsappUrl(attendee.phone) };
      });
      // Lo accionable primero: los que no vinieron (o siguen sin marcar), después los que vinieron.
      attendees.sort((a, b) => Number(a.attended) - Number(b.attended) || a.attendee.name.localeCompare(b.attendee.name));
      const counts = attendanceOf(
        attendees.map((a) => ({ checkedInAt: a.attended ? 'x' : null })),
        ended,
      );
      const editable = attendanceEditable(date, block.startTime, block.endTime, now);
      return {
        block,
        attendees,
        counts,
        ended,
        started,
        rate: ended ? ratePct(counts.attended, counts.booked) : null,
        editable,
        tooOld: started && !editable,
      };
    });
  }

  protected readonly classRows = computed<ClassRow[]>(() => {
    this.attendeesByKey();
    this.pending();
    return this.rowsFor(this.selectedDate());
  });

  // Total de la semana (solo clases terminadas): una línea de resumen sin pedir nada más al servidor.
  protected readonly weekTotals = computed<{ counts: AttendanceCounts; rate: number | null }>(() => {
    this.attendeesByKey();
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

  protected blockTitle(row: ClassRow): string {
    return `${row.block.startTime.slice(0, 5)} · ${row.block.label}`;
  }

  /** Se puede cancelar la reserva de un admin solo en clases que no pasaron (mismo criterio del backend). */
  protected canCancel(): boolean {
    return this.selectedDate() >= this.today;
  }

  // ---- navegación ----

  protected selectDay(date: string): void {
    this.selectedDate.set(date);
    this.expandedBlockId.set(null);
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

  protected toggleClass(blockId: number): void {
    this.expandedBlockId.update((current) => (current === blockId ? null : blockId));
  }

  protected openSummary(): void {
    this.summaryAnchor.set(this.anchor());
    this.summaryMode.set('week');
    this.view.set('summary');
  }

  protected closeSummary(): void {
    this.view.set('day');
  }

  protected setSummaryMode(mode: PeriodMode): void {
    this.summaryMode.set(mode);
  }

  protected shiftSummary(direction: -1 | 1): void {
    this.summaryAnchor.set(shiftAnchor(this.summaryAnchor(), this.summaryMode(), direction));
  }

  // Se corrigió una asistencia desde el detalle de un socio dentro del resumen: la semana quedó vieja.
  protected onSummaryChanged(): void {
    this.loadedRanges.clear();
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
        const key = this.key(row.block.id, date);
        this.attendeesByKey.update((map) => ({
          ...map,
          [key]: (map[key] ?? []).map((a) =>
            a.reservationId === mark.reservationId
              ? { ...a, checkedInAt: mark.checkedInAt, checkinMethod: mark.checkinMethod }
              : a,
          ),
        }));
        this.dropPending(id);
        this.toast(target ? `${item.attendee.name}: marcado como que vino.` : `${item.attendee.name}: marcado como que no vino.`);
      },
      error: (err) => {
        this.dropPending(id);
        this.toast(errorText(err, 'No pudimos guardar el cambio. Intenta nuevamente.'), 'danger');
      },
    });
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
