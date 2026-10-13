import { PaymentPreview } from '../models/member.model';
import { addDays } from './attendance';
import { formatIsoDate } from './date-format';

/** Fechas y textos de "Marcar pago" con fecha de inicio y clases ya usadas (ver GymService.previewPlanPayment). */

function parseIso(iso: string): [number, number, number] {
  const [y, m, d] = iso.split('-').map(Number);
  return [y, m, d];
}

function toIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** La fecha más antigua que se acepta: un mes atrás + 1 día (con un mes exacto el plan ya vencería hoy). */
export function minStartDate(todayIso: string): string {
  const [y, m, d] = parseIso(todayIso);
  // El mismo día del mes anterior; si ese mes no tiene el día (31 de marzo → febrero) se usa su último día, como
  // hace el servidor al sumar un mes (plusMonths).
  const daysInLastMonth = new Date(y, m - 1, 0).getDate();
  return addDays(toIso(new Date(y, m - 2, Math.min(d, daysInLastMonth))), 1);
}

export interface StartDateShortcut {
  label: string;
  date: string;
}

/** Atajos del selector: Hoy, hace 1 semana y hace 2 semanas (ninguno pasa del límite de un mes). */
export function startDateShortcuts(todayIso: string): StartDateShortcut[] {
  return [
    { label: 'Hoy', date: todayIso },
    { label: 'Hace 1 semana', date: addDays(todayIso, -7) },
    { label: 'Hace 2 semanas', date: addDays(todayIso, -14) },
  ];
}

/** "Vigente del 28-09-2026 al 27-10-2026". */
export function periodText(preview: Pick<PaymentPreview, 'periodStart' | 'periodEnd'>): string {
  return `Vigente del ${formatIsoDate(preview.periodStart)} al ${formatIsoDate(preview.periodEnd)}`;
}

/** Cuántas clases le quedarían y por qué ("8 del plan − 4 usadas − 1 reserva en la app"); null en un plan sin tope. */
export function remainingText(preview: PaymentPreview): string | null {
  if (preview.remaining === null || preview.monthlyClasses === null) {
    return null;
  }
  const parts = [`${preview.monthlyClasses} del plan`];
  if (preview.usedOutside > 0) {
    parts.push(`${preview.usedOutside} ya usadas`);
  }
  if (preview.bookedInApp > 0) {
    parts.push(`${preview.bookedInApp} ${preview.bookedInApp === 1 ? 'reserva' : 'reservas'} en la app`);
  }
  const left = Math.max(preview.remaining, 0);
  return `${parts.join(' − ')} → le ${left === 1 ? 'queda' : 'quedan'} ${left} ${left === 1 ? 'clase' : 'clases'}`;
}

/** Último día del período que empieza en `startIso`: un mes después, menos un día (igual que el servidor: plusMonths).
 *  Se calcula acá para poder mostrar "Vigente del X al Y" aunque el servidor no responda la vista previa. */
export function periodEndOf(startIso: string): string {
  const [y, m, d] = parseIso(startIso);
  const daysInNextMonth = new Date(y, m + 1, 0).getDate();
  return addDays(toIso(new Date(y, m, Math.min(d, daysInNextMonth))), -1);
}
