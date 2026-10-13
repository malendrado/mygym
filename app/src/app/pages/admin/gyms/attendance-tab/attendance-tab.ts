import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { chevronBackOutline, chevronForwardOutline } from 'ionicons/icons';
import { PeriodMode, periodOf, santiagoNow, shiftAnchor } from '../../../../core/utils/attendance';
import { AttendanceSummaryView, CancelReservationRequest } from '../attendance-summary/attendance-summary';

addIcons({
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
});

/**
 * Sub-pestaña "Asistencia" de Clases: el consolidado de la semana o el mes (vinieron / no vinieron / sin marcar,
 * % por clase y por socio). Es la vista de resumen que antes vivía detrás de "Ver resumen" en Historial, con su
 * selector Semana/Mes y la navegación del período; el detalle lo sigue dando AttendanceSummaryView.
 *
 * Compartido por gym-admin y gym-form (`gymId` null = gym del JWT). `anchor` es la fecha desde la que abre (la semana
 * que se estaba mirando en la Agenda).
 */
@Component({
  selector: 'app-attendance-tab',
  imports: [IonIcon, AttendanceSummaryView],
  templateUrl: './attendance-tab.html',
  styleUrl: './attendance-tab.scss',
})
export class AttendanceTab {
  @Input() set anchor(value: string | null) {
    if (value && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      this.summaryAnchor.set(value);
    }
  }
  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  @Input() demo = false;
  /** El padre lo sube cuando cambió algo por su lado: se vuelve a pedir el resumen. */
  @Input() refreshToken = 0;
  /** Si viene un socio (buscador compartido de Clases), se muestra solo su detalle de asistencia en el período. */
  @Input() memberId: number | null = null;
  @Output() readonly cancelRequested = new EventEmitter<CancelReservationRequest>();
  /** Se corrigió una asistencia desde el detalle de un socio: la Agenda quedó vieja. */
  @Output() readonly attendanceChanged = new EventEmitter<void>();

  protected readonly summaryMode = signal<PeriodMode>('week');
  private readonly summaryAnchor = signal(santiagoNow().date);
  protected readonly summaryPeriod = computed(() => periodOf(this.summaryAnchor(), this.summaryMode()));

  protected setMode(mode: PeriodMode): void {
    this.summaryMode.set(mode);
  }

  protected shift(direction: -1 | 1): void {
    this.summaryAnchor.set(shiftAnchor(this.summaryAnchor(), this.summaryMode(), direction));
  }
}
