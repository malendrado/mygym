import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonProgressBar,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  checkmarkCircleOutline,
  cloudDownloadOutline,
  cloudUploadOutline,
  documentTextOutline,
  rocketOutline,
} from 'ionicons/icons';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { retry, timeout } from 'rxjs/operators';
import * as XLSX from 'xlsx';
import { GymPlan } from '../../../../core/models/gym.model';
import { MemberImportRow, MemberImportRowResult } from '../../../../core/models/member.model';
import { AuthService } from '../../../../core/services/auth.service';
import { MemberService } from '../../../../core/services/member.service';

addIcons({
  'cloud-download-outline': cloudDownloadOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'document-text-outline': documentTextOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'alert-circle-outline': alertCircleOutline,
  'rocket-outline': rocketOutline,
});

type Stage = 'start' | 'preview' | 'importing' | 'done';

interface ParsedRow {
  rowNumber: number;
  name: string;
  email: string;
  planName: string | null;
  planId: number | null;
  amountClp: number | null;
  expiresAtIso: string | null;
  expiresAtLabel: string | null;
  usedSessions: number | null;
  // 'warning' = se puede importar igual (el admin decide), a diferencia de 'error' que bloquea
  // la fila del todo — ver parseRow: email que no es Gmail o "clases usadas" en un plan libre.
  status: 'ok' | 'warning' | 'error';
  statusLabel: string;
  error: string | null;
  included: boolean;
}

interface FailedImport {
  email: string;
  error: string;
}

// Sinónimos de encabezado (sin tildes, en minúscula) → columna interna — así el archivo del
// admin no tiene que copiar la plantilla letra por letra, solo tener una fila de encabezados
// reconocible en cualquier orden.
const HEADER_SYNONYMS: Record<string, keyof ColumnIndex> = {
  nombre: 'name',
  email: 'email',
  correo: 'email',
  'correo electronico': 'email',
  plan: 'plan',
  'monto pagado clp': 'amount',
  'monto pagado (clp)': 'amount',
  'monto pagado': 'amount',
  monto: 'amount',
  'fecha de vencimiento del plan': 'expiresAt',
  'fecha de vencimiento': 'expiresAt',
  vencimiento: 'expiresAt',
  'clases usadas': 'usedSessions',
  'clases ocupadas': 'usedSessions',
  'sesiones usadas': 'usedSessions',
};

interface ColumnIndex {
  name: number;
  email: number;
  plan: number;
  amount: number;
  expiresAt: number;
  usedSessions: number;
}

const GMAIL_WARNING =
  'Este email no es de Gmail — si esta persona no tiene una cuenta de Google con este correo, no va a poder entrar.';

const CHUNK_SIZE = 20;
const REQUEST_TIMEOUT_MS = 45000;

function pad2(n: number): string {
  return n.toString().padStart(2, '0');
}

function normalizeHeader(value: unknown): string {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
}

function todayIsoDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

@Component({
  selector: 'app-import-members-modal',
  imports: [IonHeader, IonFooter, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon, IonProgressBar],
  templateUrl: './import-members-modal.html',
  styleUrl: './import-members-modal.scss',
})
export class ImportMembersModal {
  @Input() set plans(value: GymPlan[]) {
    this._plans.set(value.filter((p) => p.active));
  }

  @Input() set existingEmails(value: string[]) {
    this._existingEmails.set(new Set(value.map((e) => e.trim().toLowerCase())));
  }

  @Output() dismiss = new EventEmitter<void>();
  // Se emite si al menos un socio se importó bien, para que gym-admin.ts recargue la lista —
  // no espera a que el admin cierre el modal, el resumen final queda visible igual.
  @Output() imported = new EventEmitter<void>();

  private readonly memberService = inject(MemberService);
  private readonly authService = inject(AuthService);

  // El backend ya bloquea de verdad cualquier escritura de un DEMO_ADMIN (403 real, ver
  // SecurityConfig — ningún endpoint bajo /api/gym-admin/** que no sea GET admite ese rol). Acá
  // chequeamos ANTES de intentar la llamada — no tiene sentido esperar 45s+reintento de timeout
  // para un 403 que ya sabemos que va a pasar, y el mensaje que le mostramos al admin demo tiene
  // que explicar que es una restricción a propósito, no un error real del sistema.
  protected readonly isDemoAdmin = computed(() => this.authService.currentUser()?.role === 'DEMO_ADMIN');
  protected readonly demoBlocked = signal(false);

  private readonly _plans = signal<GymPlan[]>([]);
  private readonly _existingEmails = signal<Set<string>>(new Set());

  protected readonly stage = signal<Stage>('start');
  protected readonly fileError = signal<string | null>(null);
  protected readonly parsing = signal(false);
  protected readonly rows = signal<ParsedRow[]>([]);
  protected readonly progress = signal({ done: 0, total: 0 });
  protected readonly currentChunkLabel = signal('');
  protected readonly summary = signal<{ imported: number; failed: number } | null>(null);
  protected readonly failedDetails = signal<FailedImport[]>([]);

  protected readonly okRows = computed(() => this.rows().filter((r) => r.status === 'ok'));
  protected readonly warningRows = computed(() => this.rows().filter((r) => r.status === 'warning'));
  protected readonly errorRows = computed(() => this.rows().filter((r) => r.status === 'error'));
  protected readonly includedCount = computed(
    () => this.rows().filter((r) => r.status !== 'error' && r.included).length,
  );
  protected readonly progressPercent = computed(() => {
    const p = this.progress();
    return p.total === 0 ? 0 : p.done / p.total;
  });

  protected downloadTemplate(): void {
    const headers = ['Nombre', 'Email', 'Plan', 'Monto pagado CLP', 'Fecha de vencimiento', 'Clases usadas'];
    const example1 = ['Javiera Pulgar', 'javiera@gmail.com', '', '', '', ''];
    const example2 = ['Diego Soto', 'diego@gmail.com', 'Plan Libre', 39990, '15-10-2026', ''];
    const example3 = ['Camila Rojas', 'camila@gmail.com', 'Plan 8 clases', 29990, '20-10-2026', 3];
    const sheet = XLSX.utils.aoa_to_sheet([headers, example1, example2, example3]);
    sheet['!cols'] = [{ wch: 22 }, { wch: 28 }, { wch: 18 }, { wch: 18 }, { wch: 20 }, { wch: 16 }];
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, 'Socios');
    XLSX.writeFile(workbook, 'plantilla-socios-mygym.xlsx');
  }

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
      const workbook = XLSX.read(buffer, { type: 'array', cellDates: true });
      const sheetName = workbook.SheetNames[0];
      if (!sheetName) {
        throw new Error('El archivo no tiene ninguna hoja con datos.');
      }
      const grid = XLSX.utils.sheet_to_json<unknown[]>(workbook.Sheets[sheetName], {
        header: 1,
        defval: null,
        raw: true,
      });
      this.parseGrid(grid);
    } catch (e) {
      this.fileError.set(
        e instanceof Error && e.message
          ? e.message
          : 'No pudimos leer ese archivo. Revisa que sea un Excel (.xlsx) válido.',
      );
    } finally {
      this.parsing.set(false);
    }
  }

  private parseGrid(grid: unknown[][]): void {
    const dataRows = grid.filter((row) => row && row.some((cell) => cell !== null && cell !== ''));
    if (dataRows.length === 0) {
      this.fileError.set('El archivo está vacío.');
      return;
    }

    const headerRow = dataRows[0];
    const columnIndex: Partial<ColumnIndex> = {};
    headerRow.forEach((cell, idx) => {
      const key = HEADER_SYNONYMS[normalizeHeader(cell)];
      if (key && columnIndex[key] === undefined) {
        columnIndex[key] = idx;
      }
    });
    if (columnIndex.name === undefined || columnIndex.email === undefined) {
      this.fileError.set(
        'No encontramos las columnas "Nombre" y/o "Email" — revisa que la primera fila tenga los encabezados de la plantilla.',
      );
      return;
    }

    const body = dataRows.slice(1);
    if (body.length === 0) {
      this.fileError.set('El archivo no tiene filas de datos, solo encabezados.');
      return;
    }
    if (body.length > 2000) {
      this.fileError.set('Ese archivo tiene demasiadas filas (más de 2000). Divídelo en varios archivos.');
      return;
    }

    const activePlans = this._plans();
    const existingEmails = this._existingEmails();
    const seenInFile = new Set<string>();
    const parsed = body.map((cells, i) =>
      this.parseRow(cells, i + 2, columnIndex as ColumnIndex, activePlans, existingEmails, seenInFile),
    );
    this.rows.set(parsed);
    this.stage.set('preview');
  }

  private parseRow(
    cells: unknown[],
    rowNumber: number,
    columnIndex: ColumnIndex,
    activePlans: GymPlan[],
    existingEmails: Set<string>,
    seenInFile: Set<string>,
  ): ParsedRow {
    const cellAt = (idx: number | undefined): unknown => (idx === undefined ? null : (cells[idx] ?? null));
    const name = String(cellAt(columnIndex.name) ?? '').trim();
    const email = String(cellAt(columnIndex.email) ?? '').trim();
    const planRaw = cellAt(columnIndex.plan);
    const amountRaw = cellAt(columnIndex.amount);
    const expiresRaw = cellAt(columnIndex.expiresAt);
    const usedSessionsRaw = cellAt(columnIndex.usedSessions);

    const base: Omit<ParsedRow, 'status' | 'statusLabel' | 'error'> = {
      rowNumber,
      name,
      email,
      planName: planRaw !== null && String(planRaw).trim() !== '' ? String(planRaw).trim() : null,
      planId: null,
      amountClp: null,
      expiresAtIso: null,
      expiresAtLabel: null,
      usedSessions: null,
      included: true,
    };
    const error = (message: string): ParsedRow => ({ ...base, status: 'error', statusLabel: '', error: message });
    // 'warning': la fila se puede importar igual (checkbox queda tildado), a diferencia de
    // 'error' — ver GMAIL_WARNING y el caso "clases usadas" en un plan libre más abajo.
    const finish = (statusLabel: string, warnings: string[], fields: Partial<ParsedRow> = {}): ParsedRow =>
      warnings.length > 0
        ? { ...base, ...fields, status: 'warning', statusLabel, error: warnings.join(' · ') }
        : { ...base, ...fields, status: 'ok', statusLabel, error: null };

    if (!name) {
      return error(`Fila ${rowNumber}: falta el nombre.`);
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email || !emailRegex.test(email)) {
      return error('Email inválido o vacío.');
    }
    const normalizedEmail = email.toLowerCase();
    if (existingEmails.has(normalizedEmail)) {
      return error('Ya existe un socio con ese email en este gimnasio.');
    }
    if (seenInFile.has(normalizedEmail)) {
      return error('Email repetido en este mismo archivo.');
    }
    seenInFile.add(normalizedEmail);

    const warnings: string[] = [];
    if (!normalizedEmail.endsWith('@gmail.com')) {
      warnings.push(GMAIL_WARNING);
    }

    const hasPlan = base.planName !== null;
    const hasAmount = amountRaw !== null && String(amountRaw).trim() !== '';
    const hasExpires = expiresRaw !== null && String(expiresRaw).trim() !== '';
    const hasUsedSessions = usedSessionsRaw !== null && String(usedSessionsRaw).trim() !== '';

    if (!hasPlan && !hasAmount && !hasExpires) {
      if (hasUsedSessions) {
        return error('"Clases usadas" solo aplica si también completas Plan, Monto pagado y Fecha de vencimiento.');
      }
      return finish('Se invita sin plan', warnings);
    }
    if (!(hasPlan && hasAmount && hasExpires)) {
      return error('Completa Plan, Monto pagado y Fecha de vencimiento juntos, o deja los tres vacíos.');
    }

    const plan = activePlans.find((p) => p.name.trim().toLowerCase() === base.planName!.toLowerCase());
    if (!plan) {
      return error(`El plan "${base.planName}" no coincide con ningún plan activo de tu gimnasio.`);
    }

    const amount = this.parseAmount(amountRaw);
    if (amount === null || amount <= 0) {
      return { ...error('Monto pagado inválido.'), planId: plan.id };
    }

    const dateParts = this.parseDateParts(expiresRaw);
    if (!dateParts) {
      return { ...error('Fecha de vencimiento ilegible — usa el formato dd-mm-aaaa.'), planId: plan.id, amountClp: amount };
    }

    let usedSessions: number | null = null;
    if (hasUsedSessions) {
      const rawUsed = this.parseAmount(usedSessionsRaw);
      if (rawUsed === null || rawUsed < 0) {
        return { ...error('"Clases usadas" inválido.'), planId: plan.id, amountClp: amount };
      }
      if (plan.monthlyClasses === null) {
        warnings.push('"Clases usadas" no aplica a un plan libre/ilimitado — se ignora para esta fila.');
      } else if (rawUsed > plan.monthlyClasses) {
        return {
          ...error(`"Clases usadas" no puede ser mayor al cupo del plan (${plan.monthlyClasses}).`),
          planId: plan.id,
          amountClp: amount,
        };
      } else {
        usedSessions = rawUsed;
      }
    }

    // Instante fijo a mediodía UTC del día elegido (nunca a partir del huso horario del
    // navegador del admin) — Santiago siempre está detrás de UTC, así que convertir este
    // instante a America/Santiago en el backend cae siempre en el mismo día calendario.
    const expiresInstant = new Date(Date.UTC(dateParts.y, dateParts.m - 1, dateParts.d, 12, 0, 0));
    const expiresAtLabel = `${pad2(dateParts.d)}-${pad2(dateParts.m)}-${dateParts.y}`;
    const expiresIsoDate = `${dateParts.y}-${pad2(dateParts.m)}-${pad2(dateParts.d)}`;
    const statusLabel =
      expiresIsoDate < todayIsoDate() ? `Vencido desde ${expiresAtLabel}` : `Activo hasta ${expiresAtLabel}`;

    return finish(statusLabel, warnings, {
      planId: plan.id,
      amountClp: amount,
      expiresAtIso: expiresInstant.toISOString(),
      expiresAtLabel,
      usedSessions,
    });
  }

  private parseAmount(raw: unknown): number | null {
    if (typeof raw === 'number' && !isNaN(raw)) {
      return Math.round(raw);
    }
    const digits = String(raw ?? '').replace(/[^\d]/g, '');
    return digits ? parseInt(digits, 10) : null;
  }

  private parseDateParts(raw: unknown): { y: number; m: number; d: number } | null {
    if (raw instanceof Date && !isNaN(raw.getTime())) {
      return { y: raw.getUTCFullYear(), m: raw.getUTCMonth() + 1, d: raw.getUTCDate() };
    }
    const match = String(raw ?? '')
      .trim()
      .match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
    if (!match) {
      return null;
    }
    const d = Number(match[1]);
    const m = Number(match[2]);
    const y = Number(match[3]);
    if (m < 1 || m > 12 || d < 1 || d > 31) {
      return null;
    }
    return { y, m, d };
  }

  protected toggleRowIncluded(row: ParsedRow): void {
    this.rows.update((list) => list.map((r) => (r === row ? { ...r, included: !r.included } : r)));
  }

  protected startOver(): void {
    this.rows.set([]);
    this.fileError.set(null);
    this.stage.set('start');
  }

  protected async confirmImport(): Promise<void> {
    const toImport = this.rows().filter((r) => r.status !== 'error' && r.included);
    if (toImport.length === 0) {
      return;
    }
    if (this.isDemoAdmin()) {
      this.demoBlocked.set(true);
      this.stage.set('done');
      return;
    }
    this.stage.set('importing');
    this.progress.set({ done: 0, total: toImport.length });

    const chunks: ParsedRow[][] = [];
    for (let i = 0; i < toImport.length; i += CHUNK_SIZE) {
      chunks.push(toImport.slice(i, i + CHUNK_SIZE));
    }

    let importedCount = 0;
    const failedDetails: FailedImport[] = [];

    for (const chunk of chunks) {
      const from = this.progress().done + 1;
      const to = this.progress().done + chunk.length;
      this.currentChunkLabel.set(`Procesando ${from}–${to} de ${toImport.length}...`);
      const payload: MemberImportRow[] = chunk.map((r) => ({
        name: r.name,
        email: r.email,
        planId: r.planId,
        amountClp: r.amountClp,
        expiresAt: r.expiresAtIso,
        usedSessions: r.usedSessions,
      }));
      try {
        const results = await firstValueFrom(
          this.memberService.importBatch(payload).pipe(timeout(REQUEST_TIMEOUT_MS), retry(1)),
        );
        for (const result of results as MemberImportRowResult[]) {
          if (result.success) {
            importedCount++;
          } else {
            failedDetails.push({ email: result.email, error: result.error ?? 'Error desconocido.' });
          }
        }
      } catch (err) {
        // Fallback por si isDemoAdmin() no alcanzó a detectarlo antes de empezar (ej. el rol
        // cambió en otra pestaña) — un 403 acá es casi seguro el bloqueo real de escritura del
        // backend para DEMO_ADMIN, no un problema de conexión. Corta el resto de los bloques:
        // todos van a fallar exactamente igual, no tiene sentido seguir intentando.
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.demoBlocked.set(true);
          this.stage.set('done');
          return;
        }
        chunk.forEach((r) =>
          failedDetails.push({ email: r.email, error: 'No se pudo conectar con el servidor para este bloque.' }),
        );
      }
      this.progress.update((p) => ({ ...p, done: p.done + chunk.length }));
    }

    this.summary.set({ imported: importedCount, failed: failedDetails.length });
    this.failedDetails.set(failedDetails);
    this.stage.set('done');
    if (importedCount > 0) {
      this.imported.emit();
    }
  }

  protected close(): void {
    this.dismiss.emit();
  }
}
