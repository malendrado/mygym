import { MemberWorkoutLog } from '../models/workout.model';

/** Filtros del historial de rutina: se aplican SOLO sobre los registros ya cargados (las ventanas de 7 días que el
 *  servidor ya entregó); no piden nada al backend. */
export interface WorkoutLogFilter {
  /** Título del día de rutina hecho (o OTHER_DAY para texto libre); null = todos. */
  day: string | null;
  /** Solo clases en que hizo algo distinto de lo que le tocaba. */
  onlyDeviated: boolean;
  /** Texto a buscar en ejercicios y notas. */
  query: string;
}

export const NO_WORKOUT_FILTER: WorkoutLogFilter = { day: null, onlyDeviated: false, query: '' };

/** Clave del chip para los registros donde el socio escribió texto libre en vez de elegir un día del plan. */
export const OTHER_DAY = 'Otro';

/** Se desvió si escribió texto libre (planDayId null) o eligió un día distinto del sugerido. */
export function isDeviated(log: MemberWorkoutLog): boolean {
  return log.planDayId === null || log.planDayId !== log.suggestedPlanDayId;
}

/** Día de rutina que realmente hizo, como clave de filtro. */
export function doneDayKey(log: MemberWorkoutLog): string {
  return log.planDayId !== null ? (log.planDayTitle ?? OTHER_DAY) : OTHER_DAY;
}

/** Días distintos que aparecen en los registros cargados, en orden natural ("Día 2" antes que "Día 10"). */
export function dayOptions(logs: MemberWorkoutLog[]): string[] {
  const keys = new Set(logs.map(doneDayKey));
  return [...keys].sort((a, b) => {
    if (a === OTHER_DAY) return 1;
    if (b === OTHER_DAY) return -1;
    return a.localeCompare(b, 'es', { numeric: true });
  });
}

export function isFiltering(filter: WorkoutLogFilter): boolean {
  return filter.day !== null || filter.onlyDeviated || filter.query.trim().length > 0;
}

/** Minúsculas y sin tildes: "sentadilla" encuentra "Sentadilla" y "pesó" encuentra "peso". */
function fold(char: string): string {
  return char.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export function normalizeText(text: string): string {
  return [...text].map(fold).join('');
}

export function filterWorkoutLogs(logs: MemberWorkoutLog[], filter: WorkoutLogFilter): MemberWorkoutLog[] {
  const query = normalizeText(filter.query.trim());
  return logs.filter((log) => {
    if (filter.day !== null && doneDayKey(log) !== filter.day) {
      return false;
    }
    if (filter.onlyDeviated && !isDeviated(log)) {
      return false;
    }
    if (query === '') {
      return true;
    }
    return (log.exercises ?? []).some((e) => normalizeText(`${e.exercise} ${e.notes ?? ''}`).includes(query));
  });
}

export interface HighlightPart {
  text: string;
  match: boolean;
}

/** Parte `text` en tramos para resaltar las coincidencias con `query` (sin tildes ni mayúsculas), sin usar innerHTML. */
export function highlightParts(text: string, query: string): HighlightPart[] {
  const needle = normalizeText(query.trim());
  if (needle === '' || text === '') {
    return [{ text, match: false }];
  }
  // Un carácter original puede plegarse a 0 o varios; este mapa devuelve cada posición plegada a su posición original.
  const chars = [...text];
  let folded = '';
  const origin: number[] = [];
  chars.forEach((c, i) => {
    const f = fold(c);
    for (let k = 0; k < f.length; k++) {
      origin.push(i);
    }
    folded += f;
  });
  const parts: HighlightPart[] = [];
  let cursor = 0; // índice en `chars`
  let from = 0;
  while (from <= folded.length) {
    const hit = folded.indexOf(needle, from);
    if (hit === -1) {
      break;
    }
    const start = origin[hit];
    const end = origin[hit + needle.length - 1] + 1;
    if (start > cursor) {
      parts.push({ text: chars.slice(cursor, start).join(''), match: false });
    }
    parts.push({ text: chars.slice(start, end).join(''), match: true });
    cursor = end;
    from = hit + needle.length;
  }
  if (cursor < chars.length) {
    parts.push({ text: chars.slice(cursor).join(''), match: false });
  }
  return parts.length > 0 ? parts : [{ text, match: false }];
}
