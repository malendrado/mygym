import { addDays } from './attendance';

/** Semana en curso de lunes a domingo (regla "socios con clases fijas: solo la semana en curso", ver FixedWeekRule). */

function weekday(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = domingo
}

/** Lunes de la semana de `iso`. */
export function weekMonday(iso: string): string {
  const day = weekday(iso);
  return addDays(iso, day === 0 ? -6 : 1 - day);
}

/** Domingo de la semana de `iso`. */
export function weekSunday(iso: string): string {
  return addDays(weekMonday(iso), 6);
}

/** true si la clase cae fuera de la semana (lunes a domingo) de `todayIso`: para un socio con fijas, está bloqueada. */
export function isOutsideCurrentWeek(classDateIso: string, todayIso: string): boolean {
  return classDateIso < weekMonday(todayIso) || classDateIso > weekSunday(todayIso);
}

/** Lunes en que se abre la semana siguiente a la de `todayIso`. */
export function nextWeekMonday(todayIso: string): string {
  return addDays(weekMonday(todayIso), 7);
}
