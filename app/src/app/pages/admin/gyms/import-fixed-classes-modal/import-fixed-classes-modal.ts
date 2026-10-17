import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { IonButton, IonButtons, IonContent, IonFooter, IonHeader, IonIcon, IonProgressBar, IonTitle, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  checkmarkCircleOutline,
  cloudDownloadOutline,
  cloudUploadOutline,
  documentTextOutline,
  removeCircleOutline,
  rocketOutline,
} from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { of, throwError } from 'rxjs';
import { retry, timeout } from 'rxjs/operators';
import * as XLSX from 'xlsx';
import { trackDirty } from '../../../../core/utils/dirty-tracker';
import { FixedClassImportRow, FixedClassRowStatus } from '../../../../core/models/fixed-class.model';
import { DayOfWeek, GymBlock, sortBlocksBySchedule } from '../../../../core/models/gym.model';
import { AuthService } from '../../../../core/services/auth.service';
import { errorText } from '../../../../core/utils/error-text';
import { FixedClassService } from '../../../../core/services/fixed-class.service';
import { dayLongLabel, normalizeText, parseDay, parseTime } from '../../../../core/utils/fixed-class-import';

addIcons({
  'cloud-download-outline': cloudDownloadOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'document-text-outline': documentTextOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'alert-circle-outline': alertCircleOutline,
  'remove-circle-outline': removeCircleOutline,
  'rocket-outline': rocketOutline,
});

type Stage = 'start' | 'validating' | 'preview' | 'importing' | 'done';

type ParsedStatus = 'ok' | 'warning' | 'error' | 'omitted';

interface ParsedRow {
  rowNumber: number;
  email: string;
  dayLabel: string;
  timeLabel: string;
  className: string;
  memberName: string | null;
  status: ParsedStatus;
  /** Lo que ve el admin: qué clase es (ok), qué advertencia hay o qué está mal (error). */
  message: string;
  /** Solo las filas con formato legible viajan al servidor. */
  payload: FixedClassImportRow | null;
}

interface MemberOutcome {
  email: string;
  name: string | null;
  ok: boolean;
  message: string;
}

const HEADER_SYNONYMS: Record<string, keyof ColumnIndex> = {
  email: 'email',
  correo: 'email',
  'correo electronico': 'email',
  dia: 'day',
  'dia de la semana': 'day',
  hora: 'time',
  horario: 'time',
  'hora de inicio': 'time',
  clase: 'className',
  'nombre de la clase': 'className',
  'nombre clase': 'className',
  actividad: 'className',
};

interface ColumnIndex {
  email: number;
  day: number;
  time: number;
  className: number;
}

const MAX_ROWS = 1000;
// Socios por solicitud al confirmar: las filas de un socio van TODAS juntas (el archivo reemplaza su
// set completo) y cada socio se reserva por separado en el servidor, así que se manda de a pocos.
const MEMBERS_PER_CHUNK = 10;
const REQUEST_TIMEOUT_MS = 60000;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Una respuesta 4xx del servidor (validación, conflicto) no mejora reintentando.
function isClientError(e: unknown): boolean {
  const status = (e as { status?: number })?.status ?? 0;
  return status >= 400 && status < 500;
}

function statusFromServer(status: FixedClassRowStatus): ParsedStatus {
  return status === 'OK' ? 'ok' : status === 'WARNING' ? 'warning' : status === 'OMITTED' ? 'omitted' : 'error';
}

/**
 * Carga masiva de clases fijas desde Excel. Mismo flujo que el import de socios — plantilla,
 * vista previa con ok/advertencia/error y confirmación — pero la validación completa (socio
 * existe, clase = día + hora + NOMBRE, una por día) la hace el servidor ANTES de confirmar, porque
 * la lista de socios del panel está paginada y el navegador no puede saber si un email existe.
 * Un socio con alguna fila en error se omite entero: el archivo reemplaza su set completo y
 * cargarlo a medias le dejaría clases fijas incompletas.
 */
@Component({
  selector: 'app-import-fixed-classes-modal',
  imports: [IonHeader, IonFooter, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon, IonProgressBar],
  templateUrl: './import-fixed-classes-modal.html',
  // Reusa los estilos del import de socios (mismas clases import-*): un solo lugar para el look.
  styleUrls: ['../import-members-modal/import-members-modal.scss', './import-fixed-classes-modal.scss'],
})
export class ImportFixedClassesModal {
  /** null = gym-admin/profesor (el gym sale del JWT); con valor = super-admin (gymId por path). */
  @Input() gymId: number | null = null;

  @Input() set blocks(value: GymBlock[]) {
    this._blocks.set(sortBlocksBySchedule((value ?? []).filter((b) => b.active)));
  }

  @Output() dismiss = new EventEmitter<void>();
  /** Hay cambios sin guardar (el padre lo usa para no cerrar el modal sin avisar). */
  @Output() dirtyChange = new EventEmitter<boolean>();
  /** Se emite si al menos un socio quedó cargado, para que el padre recargue la lista de Socios. */
  @Output() imported = new EventEmitter<void>();

  private readonly fixedClassService = inject(FixedClassService);
  private readonly authService = inject(AuthService);

  // El backend ya bloquea cualquier escritura de un DEMO_ADMIN (403 real, ver SecurityConfig) —
  // mismo criterio que ImportMembersModal: se avisa antes de intentarlo y el mensaje explica que es a propósito.
  protected readonly isDemoAdmin = computed(() => this.authService.currentUser()?.role === 'DEMO_ADMIN');
  protected readonly demoBlocked = signal(false);

  private readonly _blocks = signal<GymBlock[]>([]);
  protected readonly stage = signal<Stage>('start');
  protected readonly fileError = signal<string | null>(null);
  protected readonly parsing = signal(false);
  protected readonly rows = signal<ParsedRow[]>([]);
  protected readonly progress = signal({ done: 0, total: 0 });
  protected readonly outcomes = signal<MemberOutcome[]>([]);
  protected readonly summary = signal<{ members: number; created: number; failed: number } | null>(null);

  // Hay borrador mientras el archivo ya se leyó y falta importarlo (o se está importando).
  private readonly dirtyTracker = trackDirty(
    () => this.stage() === 'preview' || this.stage() === 'validating' || this.stage() === 'importing',
    (dirty) => this.dirtyChange.emit(dirty),
  );

  constructor() {
    this.dirtyTracker.markClean();
  }

  protected readonly okRows = computed(() => this.rows().filter((r) => r.status === 'ok'));
  protected readonly warningRows = computed(() => this.rows().filter((r) => r.status === 'warning'));
  protected readonly errorRows = computed(() => this.rows().filter((r) => r.status === 'error'));
  protected readonly omittedRows = computed(() => this.rows().filter((r) => r.status === 'omitted'));

  /** Filas (listas para cargar) agrupadas por socio: solo socios SIN ninguna fila en error u omitida. */
  private readonly importableByEmail = computed(() => {
    const byEmail = new Map<string, ParsedRow[]>();
    for (const row of this.rows()) {
      if (row.payload && (row.status === 'ok' || row.status === 'warning')) {
        const key = row.email.toLowerCase();
        byEmail.set(key, [...(byEmail.get(key) ?? []), row]);
      }
    }
    return byEmail;
  });
  protected readonly importableMembers = computed(() => this.importableByEmail().size);
  protected readonly importableClasses = computed(() =>
    Array.from(this.importableByEmail().values()).reduce((sum, rows) => sum + rows.length, 0),
  );
  protected readonly progressPercent = computed(() => {
    const p = this.progress();
    return p.total === 0 ? 0 : p.done / p.total;
  });
  protected readonly hasBlocks = computed(() => this._blocks().length > 0);

  // ---------------------------------------------------------------------------------------
  // Plantilla
  // ---------------------------------------------------------------------------------------

  protected downloadTemplate(): void {
    const blocks = this._blocks();
    const headers = ['Email', 'Día', 'Hora', 'Clase'];
    // Los ejemplos salen de las clases reales del gym, así el admin ve el formato con SUS nombres.
    const example = (b: GymBlock | undefined, email: string): (string | number)[] =>
      b ? [email, dayLongLabel(b.dayOfWeek), b.startTime.slice(0, 5), b.label] : [email, 'Lunes', '19:00', 'CrossFit'];
    const first = blocks[0];
    const second = blocks.find((b) => b.dayOfWeek !== first?.dayOfWeek) ?? blocks[1];
    const sheet = XLSX.utils.aoa_to_sheet([
      headers,
      example(first, 'camila@gmail.com'),
      example(second, 'camila@gmail.com'),
      example(first, 'tomas@gmail.com'),
    ]);
    sheet['!cols'] = [{ wch: 28 }, { wch: 14 }, { wch: 10 }, { wch: 24 }];

    // Hoja de referencia: todas las clases activas con el nombre EXACTO para copiar y pegar.
    const reference = XLSX.utils.aoa_to_sheet([
      ['Día', 'Hora', 'Clase'],
      ...blocks.map((b) => [dayLongLabel(b.dayOfWeek), b.startTime.slice(0, 5), b.label]),
    ]);
    reference['!cols'] = [{ wch: 14 }, { wch: 10 }, { wch: 28 }];

    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, 'Clases fijas');
    XLSX.utils.book_append_sheet(workbook, reference, 'Clases del gimnasio');
    XLSX.writeFile(workbook, 'plantilla-clases-fijas-mygym.xlsx');
  }

  // ---------------------------------------------------------------------------------------
  // Lectura y validación
  // ---------------------------------------------------------------------------------------

  protected async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    this.fileError.set(null);
    this.parsing.set(true);
    try {
      const buffer = await file.arrayBuffer();
      // Sin cellDates: una celda con formato de hora llega como fracción del día (parseTime la entiende),
      // en vez de un Date con el desfase histórico de huso que SheetJS arrastra para horas sueltas.
      const workbook = XLSX.read(buffer, { type: 'array' });
      const sheetName = workbook.SheetNames[0];
      if (!sheetName) {
        throw new Error('El archivo no tiene ninguna hoja con datos.');
      }
      const grid = XLSX.utils.sheet_to_json<unknown[]>(workbook.Sheets[sheetName], { header: 1, defval: null, raw: true });
      await this.parseAndValidate(grid);
    } catch (e) {
      this.stage.set('start');
      this.fileError.set(
        e instanceof Error && e.message
          ? e.message
          : 'No pudimos leer ese archivo. Revisa que sea un Excel (.xlsx) válido.',
      );
    } finally {
      this.parsing.set(false);
    }
  }

  private async parseAndValidate(grid: unknown[][]): Promise<void> {
    const dataRows = grid.filter((row) => row && row.some((cell) => cell !== null && cell !== ''));
    if (dataRows.length === 0) {
      throw new Error('El archivo está vacío.');
    }
    const columnIndex: Partial<ColumnIndex> = {};
    dataRows[0].forEach((cell, idx) => {
      const key = HEADER_SYNONYMS[normalizeText(cell)];
      if (key && columnIndex[key] === undefined) {
        columnIndex[key] = idx;
      }
    });
    const missing = (['email', 'day', 'time', 'className'] as const).filter((k) => columnIndex[k] === undefined);
    if (missing.length > 0) {
      const names = { email: 'Email', day: 'Día', time: 'Hora', className: 'Clase' };
      throw new Error(
        `Faltan las columnas ${missing.map((k) => `"${names[k]}"`).join(', ')} — revisa que la primera fila tenga los encabezados de la plantilla.`,
      );
    }
    const body = dataRows.slice(1);
    if (body.length === 0) {
      throw new Error('El archivo no tiene filas de datos, solo encabezados.');
    }
    if (body.length > MAX_ROWS) {
      throw new Error(`Ese archivo tiene demasiadas filas (más de ${MAX_ROWS}). Divídelo en varios archivos.`);
    }

    // 1) Formato (lo que se puede revisar sin el servidor).
    const parsed = body.map((cells, i) => this.parseRow(cells, i + 2, columnIndex as ColumnIndex));

    // 2) El resto lo valida el servidor SIN escribir nada: socio existe, clase = día + hora + nombre,
    //    una por día, advertencias (sin plan, clase llena, reemplaza las actuales).
    const toValidate = parsed.filter((r) => r.payload !== null);
    this.stage.set('validating');
    if (toValidate.length > 0) {
      const results = await firstValueFrom(
        this.fixedClassService
          .validate(toValidate.map((r) => r.payload!), this.gymId)
          .pipe(timeout(REQUEST_TIMEOUT_MS), retry({ count: 1, delay: (e) => (isClientError(e) ? throwError(() => e) : of(null)) })),
      );
      results.forEach((res, i) => {
        const row = toValidate[i];
        row.status = statusFromServer(res.status);
        row.message = res.message;
        row.memberName = res.memberName;
      });
    }

    // 3) Un socio con una fila ilegible (que nunca viajó al servidor) tampoco se carga a medias:
    //    sus otras filas válidas se omiten, igual que si el error fuera del servidor.
    const emailsWithLocalError = new Set(parsed.filter((r) => r.status === 'error' && r.email).map((r) => r.email.toLowerCase()));
    for (const row of parsed) {
      if ((row.status === 'ok' || row.status === 'warning') && emailsWithLocalError.has(row.email.toLowerCase())) {
        row.status = 'omitted';
        row.message = 'Se omite a este socio: corrige sus filas con error y vuelve a subir el archivo.';
      }
    }

    this.rows.set(parsed);
    this.stage.set('preview');
  }

  private parseRow(cells: unknown[], rowNumber: number, col: ColumnIndex): ParsedRow {
    const cell = (idx: number): unknown => cells[idx] ?? null;
    const email = String(cell(col.email) ?? '').trim();
    const dayRaw = cell(col.day);
    const timeRaw = cell(col.time);
    const className = String(cell(col.className) ?? '').trim();
    const dayOfWeek: DayOfWeek | null = parseDay(dayRaw);
    const startTime = parseTime(timeRaw);

    const base = {
      rowNumber,
      email,
      dayLabel: dayOfWeek ? dayLongLabel(dayOfWeek) : String(dayRaw ?? '').trim(),
      timeLabel: startTime ?? String(timeRaw ?? '').trim(),
      className,
      memberName: null,
    };
    const fail = (message: string): ParsedRow => ({ ...base, status: 'error', message, payload: null });

    if (!email || !EMAIL_REGEX.test(email)) {
      return fail('Email inválido o vacío.');
    }
    if (!dayOfWeek) {
      return fail('No entendemos el día. Usa lunes, martes, miércoles, jueves, viernes, sábado o domingo.');
    }
    if (!startTime) {
      return fail('No entendemos la hora. Usa el formato 19:00.');
    }
    if (!className) {
      return fail("Falta el nombre de la clase. Cópialo de la hoja 'Clases del gimnasio'.");
    }
    return {
      ...base,
      status: 'ok',
      message: '',
      payload: { email, dayOfWeek, startTime, className },
    };
  }

  protected startOver(): void {
    this.rows.set([]);
    this.fileError.set(null);
    this.stage.set('start');
  }

  // ---------------------------------------------------------------------------------------
  // Confirmación
  // ---------------------------------------------------------------------------------------

  protected async confirmImport(): Promise<void> {
    const members = Array.from(this.importableByEmail().values());
    if (members.length === 0) {
      return;
    }
    if (this.isDemoAdmin()) {
      this.demoBlocked.set(true);
      this.stage.set('done');
      return;
    }
    this.stage.set('importing');
    this.progress.set({ done: 0, total: members.length });

    const outcomes: MemberOutcome[] = [];
    let createdTotal = 0;
    let loadedMembers = 0;

    for (let i = 0; i < members.length; i += MEMBERS_PER_CHUNK) {
      const chunk = members.slice(i, i + MEMBERS_PER_CHUNK);
      const payload = chunk.flatMap((rows) => rows.map((r) => r.payload!));
      try {
        const results = await firstValueFrom(
          this.fixedClassService.importRows(payload, this.gymId).pipe(timeout(REQUEST_TIMEOUT_MS), retry({ count: 1, delay: (e) => (isClientError(e) ? throwError(() => e) : of(null)) })),
        );
        for (const res of results) {
          outcomes.push({ email: res.email, name: res.memberName, ok: res.ok, message: res.message });
          if (res.ok) {
            loadedMembers++;
            createdTotal += res.created;
          }
        }
      } catch (err) {
        // Un 403 acá es casi seguro el bloqueo de escritura del DEMO_ADMIN (ver demoBlocked arriba).
        if (err instanceof Error && err.message.startsWith('Esta es una demo de solo lectura')) {
          this.demoBlocked.set(true);
          this.stage.set('done');
          return;
        }
        chunk.forEach((rows) =>
          outcomes.push({
            email: rows[0].email,
            name: rows[0].memberName,
            ok: false,
            message: errorText(err, 'No se pudo conectar con el servidor para este grupo. Vuelve a subir el archivo.'),
          }),
        );
      }
      this.progress.update((p) => ({ ...p, done: Math.min(p.total, p.done + chunk.length) }));
    }

    this.outcomes.set(outcomes);
    this.summary.set({ members: loadedMembers, created: createdTotal, failed: outcomes.filter((o) => !o.ok).length });
    this.stage.set('done');
    if (loadedMembers > 0) {
      this.imported.emit();
    }
  }

  protected close(): void {
    this.dismiss.emit();
  }
}
