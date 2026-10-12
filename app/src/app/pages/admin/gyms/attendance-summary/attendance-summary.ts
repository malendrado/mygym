import { Component, EventEmitter, Input, Output, computed, effect, inject, signal, untracked } from '@angular/core';
import { IonIcon, IonSpinner, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { checkmarkCircleOutline, chevronBackOutline, chevronForwardOutline, closeCircleOutline, logoWhatsapp } from 'ionicons/icons';
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

addIcons({
  'checkmark-circle-outline': checkmarkCircleOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
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
  @Output() readonly attendanceChanged = new EventEmitter<void>();

  private readonly periodSig = signal<Period | null>(null);
  private readonly tokenSig = signal(0);

  protected readonly summary = signal<AttendanceSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly view = signal<View>({ kind: 'summary' });

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
      if (!period) {
        return;
      }
      untracked(() => {
        if (token !== this.lastToken) {
          this.lastToken = token;
          this.cache.clear();
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
  protected readonly weekdayBars = computed(() => (this.summary()?.byWeekday ?? []).map((d) => ({ ...d, rate: ratePct(d.attended, d.booked) })));

  protected classTitle(c: AttendanceClassStat): string {
    return `${c.label} · ${WEEKDAY_NAME[c.weekday]} ${c.startTime.slice(0, 5)}`;
  }
  protected classRate(c: AttendanceClassStat): number | null {
    return ratePct(c.attended, c.booked);
  }
  protected weekdayShort(d: AttendanceWeekdayStat): string {
    return WEEKDAY_SHORT[d.weekday];
  }
  protected tone(rate: number | null): string {
    return rate === null ? 'none' : rateTone(rate);
  }
  protected memberRate(m: MemberAttendanceRow): number | null {
    return ratePct(m.attended, m.booked);
  }
  protected whatsapp(m: MemberAttendanceRow): string | null {
    return whatsappUrl(m.phone);
  }
  protected dateLabel(iso: string): string {
    const [y, mo, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('es-CL', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(y, mo - 1, d));
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

  protected openWeekday(d: AttendanceWeekdayStat): void {
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
    this.view.set({ kind: 'member', memberId, from: v.kind === 'list' ? v.filter : null });
    this.detail.set(null);
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
