import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { IonButton, IonButtons, IonContent, IonHeader, IonIcon, IonTitle, IonToolbar, ToastController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  checkmarkCircleOutline,
  chevronBackOutline,
  chevronForwardOutline,
  closeCircleOutline,
  logoWhatsapp,
  peopleOutline,
} from 'ionicons/icons';
import { AttendanceMark } from '../../../../core/models/attendance.model';
import { Attendee } from '../../../../core/models/member.model';
import { GymBlock } from '../../../../core/models/gym.model';
import { AttendanceService } from '../../../../core/services/attendance.service';
import {
  AttendanceCounts,
  attendanceEditable,
  attendanceOf,
  occurrenceEnded,
  occurrenceStarted,
  ratePct,
  rateTone,
  sumCounts,
} from '../../../../core/utils/attendance';
import { errorText } from '../../../../core/utils/error-text';
import { whatsappUrl } from '../../../../core/utils/member-profile';

addIcons({
  'checkmark-circle-outline': checkmarkCircleOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'close-circle-outline': closeCircleOutline,
  'logo-whatsapp': logoWhatsapp,
  'people-outline': peopleOutline,
});

interface ClassRow {
  block: GymBlock;
  counts: AttendanceCounts;
  ended: boolean;
  started: boolean;
  rate: number | null;
}

interface AttendeeRow {
  attendee: Attendee;
  attended: boolean;
  /** "19:04" o "19:04 · a mano"; null si no marcó. */
  stamp: string | null;
  whatsapp: string | null;
}

/**
 * Asistencia de un día: reservaron vs vinieron, por clase, y marcado a mano. Solo recibe datos del
 * padre (el Historial ya trajo la semana completa en una llamada); lo único que llama a la API es
 * el botón de marcar/desmarcar, y avisa por `changed` para que el padre actualice su caché.
 */
@Component({
  selector: 'app-attendance-modal',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon],
  templateUrl: './attendance-modal.html',
  styleUrl: './attendance-modal.scss',
})
export class AttendanceModal {
  private readonly attendanceService = inject(AttendanceService);
  private readonly toastController = inject(ToastController);

  @Input({ required: true }) set date(value: string) {
    this.dateSig.set(value);
  }
  @Input({ required: true }) dateLabel!: string;
  @Input({ required: true }) set blocks(value: GymBlock[]) {
    this.blocksSig.set(value);
  }
  @Input({ required: true }) set attendeesByBlock(value: Record<number, Attendee[]>) {
    this.attendeesSig.set(value);
  }
  /** null = el gym del JWT (admin/profesor); un id = super-admin sobre ese gym. */
  @Input() gymId: number | null = null;
  @Input() demo = false;
  @Output() readonly changed = new EventEmitter<{ blockId: number; mark: AttendanceMark }>();
  @Output() readonly dismiss = new EventEmitter<void>();

  private readonly dateSig = signal('');
  private readonly blocksSig = signal<GymBlock[]>([]);
  private readonly attendeesSig = signal<Record<number, Attendee[]>>({});
  private readonly chosenBlockId = signal<number | null>(null);
  private readonly chosenTab = signal<'attended' | 'missed' | null>(null);
  // Cambios optimistas: reservationId → true/false mientras el servidor responde.
  private readonly pending = signal<Record<number, boolean>>({});
  private readonly now = new Date();

  private effectiveAttended(a: Attendee): boolean {
    const override = this.pending()[a.reservationId];
    return override ?? a.checkedInAt !== null;
  }

  protected readonly classRows = computed<ClassRow[]>(() =>
    [...this.blocksSig()]
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
      .map((block) => {
        const ended = occurrenceEnded(this.dateSig(), block.endTime, this.now);
        const attendees = (this.attendeesSig()[block.id] ?? []).map((a) => ({
          checkedInAt: this.effectiveAttended(a) ? 'x' : null,
        }));
        const counts = attendanceOf(attendees, ended);
        return {
          block,
          counts,
          ended,
          started: occurrenceStarted(this.dateSig(), block.startTime, this.now),
          rate: ended ? ratePct(counts.attended, counts.booked) : null,
        };
      }),
  );

  // Solo clases terminadas: lo que sigue en curso no es "falta" todavía.
  protected readonly dayTotals = computed(() => sumCounts(this.classRows().filter((r) => r.ended).map((r) => r.counts)));
  protected readonly dayRate = computed(() => ratePct(this.dayTotals().attended, this.dayTotals().booked));
  protected readonly dayTone = computed(() => {
    const rate = this.dayRate();
    return rate === null ? null : rateTone(rate);
  });
  protected readonly hasUnfinished = computed(() => this.classRows().some((r) => !r.ended));

  // Un día con una sola clase se abre directo en ella.
  protected readonly selectedBlockId = computed(
    () => this.chosenBlockId() ?? (this.blocksSig().length === 1 ? this.blocksSig()[0].id : null),
  );
  protected readonly selected = computed(() => this.classRows().find((r) => r.block.id === this.selectedBlockId()) ?? null);
  protected readonly canGoBack = computed(() => this.blocksSig().length > 1);
  // Arranca en lo accionable: los que no vinieron (o siguen sin marcar); si no hay, en los que vinieron.
  protected readonly tab = computed(() => {
    const chosen = this.chosenTab();
    if (chosen) {
      return chosen;
    }
    const row = this.selected();
    return row && row.counts.missed + row.counts.pending > 0 ? 'missed' : 'attended';
  });
  protected readonly editable = computed(() => {
    const row = this.selected();
    return row ? attendanceEditable(this.dateSig(), row.block.startTime, row.block.endTime, this.now) : false;
  });
  protected readonly tooOld = computed(() => {
    const row = this.selected();
    return !!row && row.started && !this.editable();
  });

  private readonly attendeeRows = computed<AttendeeRow[]>(() => {
    const row = this.selected();
    if (!row) {
      return [];
    }
    return (this.attendeesSig()[row.block.id] ?? []).map((attendee) => {
      const attended = this.effectiveAttended(attendee);
      const stamp = attended ? this.stampOf(attendee) : null;
      return { attendee, attended, stamp, whatsapp: whatsappUrl(attendee.phone) };
    });
  });
  protected readonly attendedRows = computed(() => this.attendeeRows().filter((r) => r.attended));
  protected readonly notAttendedRows = computed(() => this.attendeeRows().filter((r) => !r.attended));

  private stampOf(a: Attendee): string {
    if (this.pending()[a.reservationId] !== undefined && a.checkedInAt === null) {
      return 'ahora · a mano';
    }
    if (!a.checkedInAt) {
      return '';
    }
    const time = new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(a.checkedInAt));
    return a.checkinMethod === 'MANUAL' ? `${time} · a mano` : time;
  }

  protected openClass(blockId: number): void {
    this.chosenBlockId.set(blockId);
    this.chosenTab.set(null);
  }

  protected backToList(): void {
    this.chosenBlockId.set(null);
    this.chosenTab.set(null);
  }

  protected setTab(tab: 'attended' | 'missed'): void {
    this.chosenTab.set(tab);
  }

  protected blockTitle(row: ClassRow): string {
    return `${row.block.startTime.slice(0, 5)} – ${row.block.endTime.slice(0, 5)} · ${row.block.label}`;
  }

  protected toneOf(rate: number | null): string {
    return rate === null ? 'none' : rateTone(rate);
  }

  protected toggle(row: AttendeeRow): void {
    if (this.demo) {
      this.toast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    const blockId = this.selectedBlockId();
    if (blockId === null || !this.editable()) {
      return;
    }
    const id = row.attendee.reservationId;
    const target = !row.attended;
    this.pending.update((p) => ({ ...p, [id]: target }));
    this.attendanceService.setAttendance(id, target, this.gymId).subscribe({
      next: (mark) => {
        this.changed.emit({ blockId, mark });
        this.dropPending(id);
        this.toast(target ? `${row.attendee.name}: marcado como que vino.` : `${row.attendee.name}: marcado como que no vino.`);
      },
      error: (err) => {
        this.dropPending(id);
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
