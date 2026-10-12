import { FixedClassesSaveResult } from '../models/fixed-class.model';
import { DayOfWeek } from '../models/gym.model';

/** Sin tildes, sin mayúsculas, espacios colapsados: "  Miércoles " → "miercoles". */
export function normalizeText(value: unknown): string {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ');
}

const DAYS: Record<string, DayOfWeek> = {
  lunes: 'MONDAY',
  lun: 'MONDAY',
  martes: 'TUESDAY',
  mar: 'TUESDAY',
  miercoles: 'WEDNESDAY',
  mie: 'WEDNESDAY',
  jueves: 'THURSDAY',
  jue: 'THURSDAY',
  viernes: 'FRIDAY',
  vie: 'FRIDAY',
  sabado: 'SATURDAY',
  sab: 'SATURDAY',
  domingo: 'SUNDAY',
  dom: 'SUNDAY',
};

/** "Lunes", "lun", "Miércoles", "mie." … → día de la semana, o null si no se entiende. */
export function parseDay(raw: unknown): DayOfWeek | null {
  const key = normalizeText(raw).replace(/\.$/, '');
  return DAYS[key] ?? null;
}

function pad2(n: number): string {
  return n.toString().padStart(2, '0');
}

/**
 * "19:00", "19.00", "7:00", "19h", "19:00 hrs" o el número que entrega Excel cuando la celda tiene
 * formato de hora (fracción del día: 0,7916… = 19:00) → "HH:mm", o null si no se entiende.
 * Un número entero entre 0 y 23 se toma como la hora en punto.
 */
export function parseTime(raw: unknown): string | null {
  if (typeof raw === 'number' && !isNaN(raw)) {
    if (raw > 0 && raw < 1) {
      const minutes = Math.round(raw * 1440);
      return minutes >= 1440 ? null : `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`;
    }
    return Number.isInteger(raw) && raw >= 0 && raw <= 23 ? `${pad2(raw)}:00` : null;
  }
  const text = normalizeText(raw);
  const match = text.match(/^(\d{1,2})(?:[:.h](\d{2}))?\s*(?:hrs?|h)?$/);
  if (!match) {
    return null;
  }
  const hour = Number(match[1]);
  const minute = match[2] !== undefined ? Number(match[2]) : 0;
  if (hour > 23 || minute > 59) {
    return null;
  }
  return `${pad2(hour)}:${pad2(minute)}`;
}

/** Etiqueta corta de un día: MONDAY → "Lun". */
export function dayShortLabel(day: DayOfWeek): string {
  return { MONDAY: 'Lun', TUESDAY: 'Mar', WEDNESDAY: 'Mié', THURSDAY: 'Jue', FRIDAY: 'Vie', SATURDAY: 'Sáb', SUNDAY: 'Dom' }[day];
}

/** Etiqueta larga de un día: MONDAY → "Lunes". */
export function dayLongLabel(day: DayOfWeek): string {
  return {
    MONDAY: 'Lunes',
    TUESDAY: 'Martes',
    WEDNESDAY: 'Miércoles',
    THURSDAY: 'Jueves',
    FRIDAY: 'Viernes',
    SATURDAY: 'Sábado',
    SUNDAY: 'Domingo',
  }[day];
}

/** Mensaje del toast al guardar las clases fijas de un socio (qué se reservó y qué no). */
export function fixedClassSaveMessage(result: FixedClassesSaveResult, memberName: string): string {
  if (result.noActivePlan) {
    return result.classes.length === 0
      ? `Se quitaron las clases fijas de ${memberName}.`
      : `Clases fijas guardadas. ${memberName} no tiene plan activo: se reservan cuando pague.`;
  }
  const parts: string[] = [];
  if (result.created > 0) {
    parts.push(
      `Listo: ${result.created} ${result.created === 1 ? 'clase reservada' : 'clases reservadas'} para ${memberName}. Le enviamos un email.`,
    );
  } else {
    parts.push(result.classes.length === 0 ? `Se quitaron las clases fijas de ${memberName}.` : 'Clases fijas guardadas.');
  }
  if (result.stoppedByQuota) {
    parts.push('Ya no le quedan clases de su plan este período.');
  }
  if (result.skipped.length > 0) {
    parts.push(`${result.skipped.length} ${result.skipped.length === 1 ? 'fecha saltada' : 'fechas saltadas'} por cierre.`);
  }
  if (result.cancelled > 0) {
    parts.push(`${result.cancelled} ${result.cancelled === 1 ? 'reserva cancelada' : 'reservas canceladas'}.`);
  }
  return parts.join(' ');
}
