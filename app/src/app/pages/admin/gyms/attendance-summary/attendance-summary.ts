import { Component, EventEmitter, Input, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { IonIcon, IonSpinner, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  arrowUndoOutline,
  checkmarkCircleOutline,
  chevronBackOutline,
  chevronDownOutline,
  chevronForwardOutline,
  chevronUpOutline,
  closeCircleOutline,
  logoWhatsapp,
  personRemoveOutline,
} from 'ionicons/icons';
import {
  AttendanceClassStat,
  AttendanceMembersFilter,
  AttendanceSummary,
  AttendanceWeekdayStat,
  MemberAttendanceDetail,
  MemberAttendanceDetailRow,
  MemberAttendanceRow,
} from '../../../../core/models/attendance.model';
import { DayOfWeek } from '../../../../core/models/gym.model';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { Period, RateTone, ratePct, rateTone, variationLabel } from '../../../../core/utils/attendance';
import { errorText } from '../../../../core/utils/error-text';
import { whatsappUrl } from '../../../../core/utils/member-profile';
import { missedClassMessage } from '../../../../core/utils/whatsapp-messages';
import { TIME_BAND_OPTIONS, TimeBand, timeBandOf } from '../../../../core/utils/time-band';

addIcons({
  'alert-circle-outline': alertCircleOutline,
  'arrow-undo-outline': arrowUndoOutline,
  'person-remove-outline': personRemoveOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-down-outline': chevronDownOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'chevron-up-outline': chevronUpOutline,
  'close-circle-outline': closeCircleOutline,
  'logo-whatsapp': logoWhatsapp,
});

const WEEKDAY_NAME: Record<DayOfWeek, string> = {
  MONDAY: 'lunes',
  TUESDAY: 'martes',
  WEDNESDAY: 'miércoles',
  THURSDAY: 'jueves',
  FRIDAY: 'viernes',
  SATURDAY: 'sábado',
  SUNDAY: 'domingo',
};
const WEEKDAY_SHORT: Record<DayOfWeek, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mié',
  THURSDAY: 'Jue',
  FRIDAY: 'Vie',
  SATURDAY: 'Sáb',
  SUNDAY: 'Dom',
};

// Se crea una sola vez: dateLabel() se llama por cada fila del detalle de un socio.
const DATE_LABEL_FORMAT = new Intl.DateTimeFormat('es-CL', { weekday: 'short', day: 'numeric', month: 'short' });

const WEEKDAY_ORDER: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

// Debajo de este % una clase o franja se marca como "asistencia baja" (mismo corte que el semáforo: rojo < 50%).
const LOW_RATE = 50;
// Cuántas clases se ven antes de "Ver todas".
const CLASSES_CAP = 6;

/** Pedido de cancelar una reserva puntual desde el detalle de un socio (el padre confirma y habla con el servidor). */
export interface CancelReservationRequest {
  reservationId: number;
  memberName: string;
  classLabel: string;
}

type View =
  | { kind: 'summary' }
  | { kind: 'list'; filter: AttendanceMembersFilter }
  | { kind: 'member'; memberId: number; from: AttendanceMembersFilter | null };

/**
 * Resumen de asistencia de un período (semana o mes): cifras, tabla por clase, barras por día y
 * ranking de los que más faltan. Todo número se puede tocar y muestra los socios que hay detrás (vista
 * dentro del mismo resumen, con "← Volver"); desde ahí se llega al detalle de un socio y se puede
 * corregir su asistencia. Compartido por gym-admin y gym-form (`gymId` null = gym del JWT).
 */
@Component({
  selector: 'app-attendance-summary',
  imports: [IonIcon, IonSpinner],
  templateUrl: './attendance-summary.html',
  styleUrl: './attendance-summary.scss',
})
export class AttendanceSummaryView {
  private readonly attendanceService = inject(AttendanceService);
  private readonly toastController = inject(ToastController);

  @Input({ required: true }) set period(value: Period) {
    this.periodSig.set(value);
  }
  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  @Input() demo = false;
  /** El padre lo sube cuando cambia una asistencia desde otro lado (modal del día) para recargar. */
  @Input() set refreshToken(value: number) {
    this.tokenSig.set(value);
  }
  /**
   * Si viene un socio, el componente muestra SOLO su detalle en el período (sin resumen ni "Volver"): es lo que ven la
   * Agenda y la Asistencia cuando se elige a alguien en el buscador compartido de Clases.
   */
  @Input() set memberId(value: number | null) {
    this.memberIdSig.set(value);
  }
  @Output() readonly attendanceChanged = new EventEmitter<void>();
  @Output() readonly cancelRequested = new EventEmitter<CancelReservationRequest>();

  private readonly periodSig = signal<Period | null>(null);
  private readonly tokenSig = signal(0);
  private readonly memberIdSig = signal<number | null>(null);
  protected readonly lockedMember = computed(() => this.memberIdSig() !== null);

  protected readonly summary = signal<AttendanceSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly view = signal<View>({ kind: 'summary' });
  // Panel: A quién escribirle (los 5 que más faltan), clases de peor a mejor y mapa día × franja.
  protected readonly classesCap = CLASSES_CAP;
  protected readonly showAllClasses = signal(false);
  protected readonly topMissed = computed(() => (this.summary()?.topAbsentees ?? []).slice(0, 5));

  // Lista de socios (paginada)
  protected readonly listItems = signal<MemberAttendanceRow[]>([]);
  protected readonly listLoading = signal(false);
  protected readonly listHasNext = signal(false);
  private listPage = 0;

  // Detalle de un socio
  protected readonly detail = signal<MemberAttendanceDetail | null>(null);
  protected readonly detailLoading = signal(false);
  private readonly pending = signal<Record<number, boolean>>({});
  private dirty = false;

  // Una carga por período, no una por cada render (mismo criterio que historyLoadedRanges del Historial).
  private cache = new Map<string, AttendanceSummary>();
  private lastToken = 0;

  constructor() {
    effect(() => {
      const period = this.periodSig();
      const token = this.tokenSig();
      const memberId = this.memberIdSig();
      if (!period) {
        return;
      }
      untracked(() => {
        if (token !== this.lastToken) {
          this.lastToken = token;
          this.cache.clear();
        }
        if (memberId !== null) {
          this.showMember(memberId, null);
          return;
        }
        this.view.set({ kind: 'summary' });
        this.loadSummary(period);
      });
    });
  }

  // ---- carga ----

  private cacheKey(p: Period): string {
    return `${p.from}_${p.to}_${p.previousFrom}`;
  }

  private loadSummary(period: Period): void {
    const cached = this.cache.get(this.cacheKey(period));
    if (cached) {
      this.summary.set(cached);
      this.loadFailed.set(false);
      return;
    }
    this.loading.set(true);
    this.loadFailed.set(false);
    this.attendanceService.summary(period.from, period.to, period.previousFrom, this.gymId).subscribe({
      next: (summary) => {
        // Si mientras tanto cambió de período, esta respuesta ya no corresponde.
        if (this.periodSig() === period) {
          this.cache.set(this.cacheKey(period), summary);
          this.summary.set(summary);
          this.loading.set(false);
        }
      },
      error: () => {
        if (this.periodSig() === period) {
          this.summary.set(null);
          this.loadFailed.set(true);
          this.loading.set(false);
        }
      },
    });
  }

  protected retry(): void {
    const period = this.periodSig();
    if (period) {
      this.loadSummary(period);
    }
  }

  // ---- derivados del resumen ----

  protected readonly rate = computed(() => {
    const t = this.summary()?.totals;
    return t ? ratePct(t.attended, t.booked) : null;
  });
  protected readonly rateTone = computed<RateTone | null>(() => {
    const r = this.rate();
    return r === null ? null : rateTone(r);
  });
  protected readonly variation = computed(() => {
    const s = this.summary();
    const p = this.periodSig();
    if (!s || !p) {
      return null;
    }
    return variationLabel(this.rate(), ratePct(s.previousTotals.attended, s.previousTotals.booked), p.versus);
  });
  protected readonly isHalfWay = computed(() => {
    const p = this.periodSig();
    return !!p && p.to >= new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
  });
  // Clases de peor a mejor asistencia (a igualdad, la que tiene más reservas primero: pesa más).
  protected readonly sortedClasses = computed(() =>
    [...(this.summary()?.byClass ?? [])].sort(
      (a, b) => (ratePct(a.attended, a.booked) ?? 101) - (ratePct(b.attended, b.booked) ?? 101) || b.booked - a.booked,
    ),
  );
  protected readonly visibleClasses = computed(() => (this.showAllClasses() ? this.sortedClasses() : this.sortedClasses().slice(0, CLASSES_CAP)));

  // Mapa día × franja, armado en el navegador con lo que ya trae byClass (día + hora de cada clase): sin backend nuevo.
  // Solo se muestran los días y franjas con reservas, para que el mapa no se llene de guiones.
  protected readonly heat = computed(() => {
    const classes = this.summary()?.byClass ?? [];
    const acc = new Map<string, { booked: number; attended: number }>();
    for (const c of classes) {
      const key = `${timeBandOf(c.startTime)}|${c.weekday}`;
      const cur = acc.get(key) ?? { booked: 0, attended: 0 };
      acc.set(key, { booked: cur.booked + c.booked, attended: cur.attended + c.attended });
    }
    const totalOf = (pred: (band: TimeBand, day: DayOfWeek) => boolean) => {
      let booked = 0;
      let attended = 0;
      for (const [key, v] of acc) {
        const [band, day] = key.split('|') as [TimeBand, DayOfWeek];
        if (pred(band, day)) {
          booked += v.booked;
          attended += v.attended;
        }
      }
      return { booked, attended };
    };
    const days = WEEKDAY_ORDER.map((weekday) => ({ weekday, short: WEEKDAY_SHORT[weekday], ...totalOf((_, d) => d === weekday) })).filter(
      (d) => d.booked > 0,
    );
    const rows = TIME_BAND_OPTIONS.filter((band) => totalOf((b) => b === band.value).booked > 0).map((band) => ({
      band: band.value,
      label: band.label,
      cells: days.map((d) => {
        const v = acc.get(`${band.value}|${d.weekday}`) ?? { booked: 0, attended: 0 };
        const rate = ratePct(v.attended, v.booked);
        const dayName = WEEKDAY_NAME[d.weekday].charAt(0).toUpperCase() + WEEKDAY_NAME[d.weekday].slice(1);
        return {
          weekday: d.weekday,
          booked: v.booked,
          attended: v.attended,
          rate,
          tip: v.booked === 0 ? `${dayName} · ${band.label}: sin clases terminadas` : `${dayName} · ${band.label}: ${v.attended} de ${v.booked} (${rate}%)`,
        };
      }),
    }));
    return { days, rows };
  });

  protected classTitle(c: AttendanceClassStat): string {
    return `${c.label} · ${WEEKDAY_NAME[c.weekday]} ${c.startTime.slice(0, 5)}`;
  }
  protected classRate(c: AttendanceClassStat): number | null {
    return ratePct(c.attended, c.booked);
  }
  protected isLow(rate: number | null): boolean {
    return rate !== null && rate < LOW_RATE;
  }
  protected tone(rate: number | null): string {
    return rate === null ? 'none' : rateTone(rate);
  }
  protected memberRate(m: MemberAttendanceRow): number | null {
    return ratePct(m.attended, m.booked);
  }
  protected whatsapp(m: MemberAttendanceRow): string | null {
    return whatsappUrl(m.phone, m.missed > 0 ? missedClassMessage(m.name) : null);
  }
  protected dateLabel(iso: string): string {
    const [y, mo, d] = iso.split('-').map(Number);
    return DATE_LABEL_FORMAT.format(new Date(y, mo - 1, d));
  }
  protected methodLabel(row: MemberAttendanceDetailRow): string {
    return row.checkinMethod === 'MANUAL' ? 'a mano' : 'QR';
  }
  protected effectiveAttended(row: MemberAttendanceDetailRow): boolean {
    return this.pending()[row.reservationId] ?? row.attended;
  }

  // ---- navegación interna ----

  protected openList(filter: AttendanceMembersFilter): void {
    this.view.set({ kind: 'list', filter });
    this.listItems.set([]);
    this.listHasNext.set(false);
    this.listPage = 0;
    this.loadListPage(filter);
  }

  protected openClass(c: AttendanceClassStat): void {
    this.openList({ title: this.classTitle(c), blockId: c.blockId });
  }

  protected openWeekday(d: Pick<AttendanceWeekdayStat, 'weekday' | 'booked'>): void {
    if (d.booked === 0) {
      return;
    }
    this.openList({ title: `Todas las clases del ${WEEKDAY_NAME[d.weekday]}`, weekday: d.weekday });
  }

  protected loadMore(): void {
    const v = this.view();
    if (v.kind === 'list') {
      this.loadListPage(v.filter);
    }
  }

  private loadListPage(filter: AttendanceMembersFilter): void {
    const period = this.periodSig();
    if (!period || this.listLoading()) {
      return;
    }
    this.listLoading.set(true);
    this.attendanceService.members(period.from, period.to, filter, this.listPage, this.gymId).subscribe({
      next: (page) => {
        this.listItems.update((items) => [...items, ...page.items]);
        this.listHasNext.set(page.hasNext);
        this.listPage = page.page + 1;
        this.listLoading.set(false);
      },
      error: (err) => {
        this.listLoading.set(false);
        this.toast(errorText(err, 'No pudimos cargar la lista de socios. Intenta nuevamente.'), 'danger');
      },
    });
  }

  protected openMember(memberId: number): void {
    const v = this.view();
    this.showMember(memberId, v.kind === 'list' ? v.filter : null);
  }

  private showMember(memberId: number, from: AttendanceMembersFilter | null): void {
    this.view.set({ kind: 'member', memberId, from });
    this.detail.set(null);
    this.loadDetail(memberId);
  }

  private loadDetail(memberId: number): void {
    const period = this.periodSig();
    if (!period) {
      return;
    }
    this.detailLoading.set(true);
    this.attendanceService.memberDetail(memberId, period.from, period.to, this.gymId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.detailLoading.set(false);
      },
      error: (err) => {
        this.detailLoading.set(false);
        this.toast(errorText(err, 'No pudimos cargar el detalle del socio. Intenta nuevamente.'), 'danger');
      },
    });
  }

  protected back(): void {
    const v = this.view();
    if (v.kind === 'member' && v.from) {
      // De vuelta a la lista (recargada si se corrigió algo); el resumen se recarga recién al llegar a él.
      if (this.dirty) {
        this.openList(v.from);
      } else {
        this.view.set({ kind: 'list', filter: v.from });
      }
      return;
    }
    this.view.set({ kind: 'summary' });
    if (this.dirty) {
      this.dirty = false;
      this.cache.clear();
      const period = this.periodSig();
      if (period) {
        this.loadSummary(period);
      }
    }
  }

  // Del detalle: solo clases terminadas cuentan, igual que el servidor.
  protected readonly detailCounts = computed(() => {
    const ended = (this.detail()?.rows ?? []).filter((r) => r.ended);
    const attended = ended.filter((r) => this.effectiveAttended(r)).length;
    return { booked: ended.length, attended, missed: ended.length - attended, rate: ratePct(attended, ended.length) };
  });

  protected readonly listTitle = computed(() => {
    const v = this.view();
    return v.kind === 'list' ? v.filter.title : '';
  });

  // ---- cancelar una reserva futura desde el detalle de un socio ----

  protected cancelRow(row: MemberAttendanceDetailRow): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.cancelRequested.emit({
      reservationId: row.reservationId,
      memberName: d.member.name,
      classLabel: `${row.blockLabel} · ${this.dateLabel(row.classDate)} ${row.startTime.slice(0, 5)}`,
    });
  }

  // ---- corregir asistencia desde el detalle de un socio ----

  protected toggle(row: MemberAttendanceDetailRow): void {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    const target = !this.effectiveAttended(row);
    this.pending.update((p) => ({ ...p, [row.reservationId]: target }));
    this.attendanceService.setAttendance(row.reservationId, target, this.gymId).subscribe({
      next: (mark) => {
        this.detail.update((d) =>
          d
            ? {
                ...d,
                rows: d.rows.map((r) =>
                  r.reservationId === row.reservationId
                    ? { ...r, attended: mark.checkedInAt !== null, checkedInAt: mark.checkedInAt, checkinMethod: mark.checkinMethod }
                    : r,
                ),
              }
            : d,
        );
        this.dropPending(row.reservationId);
        this.dirty = true;
        this.attendanceChanged.emit();
        this.toast(target ? 'Marcado como que vino.' : 'Marcado como que no vino.');
      },
      error: (err) => {
        this.dropPending(row.reservationId);
        this.toast(errorText(err, 'No pudimos guardar el cambio. Intenta nuevamente.'), 'danger');
      },
    });
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
