/**
 * Reglas puras de asistencia (reservaron vs vinieron) — un solo lugar para no repetirlas en el
 * chip del día, el modal, el resumen y el ranking. "No vino" no es un estado guardado: es una
 * reserva de una clase que YA terminó y sin check-in; mientras la clase no termina, "sin marcar"
 * es solo "pendiente". Todo en hora de Chile, igual que el backend (ReservationService.isPastOccurrence).
 */

const SANTIAGO = 'America/Santiago';

export type RateTone = 'good' | 'warn' | 'bad';
export type PeriodMode = 'week' | 'month';

export interface AttendanceCounts {
  booked: number;
  attended: number;
  missed: number;
  pending: number;
}

/** Fecha y hora de "ahora" en Chile, como textos comparables ("2026-10-12", "19:04"). */
export function santiagoNow(now: Date = new Date()): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: SANTIAGO,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return { date: `${get('year')}-${get('month')}-${get('day')}`, time: `${get('hour')}:${get('minute')}` };
}

/** true si la clase ya terminó (su hora de fin quedó atrás). endTime = "19:00" o "19:00:00". */
export function occurrenceEnded(classDate: string, endTime: string, now: Date = new Date()): boolean {
  const current = santiagoNow(now);
  const end = endTime.slice(0, 5);
  return classDate < current.date || (classDate === current.date && end < current.time);
}

/** true si la clase ya empezó (puede seguir en curso). */
export function occurrenceStarted(classDate: string, startTime: string, now: Date = new Date()): boolean {
  const current = santiagoNow(now);
  return classDate < current.date || (classDate === current.date && startTime.slice(0, 5) <= current.time);
}

/** Si la clase empezó hace 10 min o menos (o ya empezó): desde ahí se puede marcar asistencia. */
export function attendanceWindowOpen(classDate: string, startTime: string, now: Date = new Date()): boolean {
  const current = santiagoNow(now);
  const [h, m] = startTime.slice(0, 5).split(':').map(Number);
  const opens = h * 60 + m - 10;
  const [ch, cm] = current.time.split(':').map(Number);
  return classDate < current.date || (classDate === current.date && ch * 60 + cm >= opens);
}

/** Ventana de marcado a mano (igual que el backend): desde 10 min antes del inicio hasta 7 días después del fin. */
export function attendanceEditable(classDate: string, startTime: string, endTime: string, now: Date = new Date()): boolean {
  if (!attendanceWindowOpen(classDate, startTime, now)) {
    return false;
  }
  const current = santiagoNow(now);
  const lastDay = addDays(classDate, 7);
  return current.date < lastDay || (current.date === lastDay && current.time <= endTime.slice(0, 5));
}

/** Cuenta reservaron / vinieron / no vinieron de una clase. Sin terminar, los sin check-in son "pendientes". */
export function attendanceOf(attendees: { checkedInAt: string | null }[], ended: boolean): AttendanceCounts {
  const attended = attendees.filter((a) => a.checkedInAt).length;
  const rest = attendees.length - attended;
  return { booked: attendees.length, attended, missed: ended ? rest : 0, pending: ended ? 0 : rest };
}

/** Suma las cuentas de varias clases. */
export function sumCounts(list: AttendanceCounts[]): AttendanceCounts {
  return list.reduce(
    (acc, c) => ({
      booked: acc.booked + c.booked,
      attended: acc.attended + c.attended,
      missed: acc.missed + c.missed,
      pending: acc.pending + c.pending,
    }),
    { booked: 0, attended: 0, missed: 0, pending: 0 },
  );
}

/** Tasa de asistencia en % entero, o null si no hay reservas (nunca "0%" cuando no hay datos). */
export function ratePct(attended: number, booked: number): number | null {
  return booked > 0 ? Math.round((attended / booked) * 100) : null;
}

/** verde ≥ 80%, amarillo 50–79%, rojo < 50%. El color nunca va solo: siempre acompaña al número. */
export function rateTone(rate: number): RateTone {
  return rate >= 80 ? 'good' : rate >= 50 ? 'warn' : 'bad';
}

/** "▲ 4 pts vs. semana pasada" / "▼ 2 pts vs. septiembre" / "= igual que …"; null si falta alguna tasa. */
export function variationLabel(current: number | null, previous: number | null, versus: string): string | null {
  if (current === null || previous === null) {
    return null;
  }
  const diff = current - previous;
  if (diff === 0) {
    return `= igual que ${versus}`;
  }
  return `${diff > 0 ? '▲' : '▼'} ${Math.abs(diff)} pts vs. ${versus}`;
}

/** Peor tasa primero (para "qué horario falla"); sin reservas al final. */
export function byWorstRate<T extends { booked: number; attended: number }>(a: T, b: T): number {
  const ra = ratePct(a.attended, a.booked);
  const rb = ratePct(b.attended, b.booked);
  if (ra === null || rb === null) {
    return ra === rb ? 0 : ra === null ? 1 : -1;
  }
  return ra - rb || b.booked - a.booked;
}

// ---- períodos (semana / mes) ----

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function parseIso(iso: string): [number, number, number] {
  const [y, m, d] = iso.split('-').map(Number);
  return [y, m, d];
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

export function addDays(iso: string, days: number): string {
  const [y, m, d] = parseIso(iso);
  return toIso(new Date(y, m - 1, d + days));
}

export function mondayOf(iso: string): string {
  const [y, m, d] = parseIso(iso);
  const dow = new Date(y, m - 1, d).getDay();
  return addDays(iso, dow === 0 ? -6 : 1 - dow);
}

export interface Period {
  mode: PeriodMode;
  from: string;
  to: string;
  /** "5 – 11 oct" / "octubre 2026". */
  label: string;
  /** Con qué se compara: "la semana pasada" / "septiembre". */
  versus: string;
  /** Primer día del período con el que se compara (termina el día antes de `from`). */
  previousFrom: string;
}

const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Período que contiene a la fecha ancla: la semana lunes–domingo o el mes calendario. */
export function periodOf(anchorIso: string, mode: PeriodMode): Period {
  if (mode === 'week') {
    const from = mondayOf(anchorIso);
    const to = addDays(from, 6);
    const [, fm, fd] = parseIso(from);
    const [, tm, td] = parseIso(to);
    return { mode, from, to, label: `${fd} ${MONTHS_SHORT[fm - 1]} – ${td} ${MONTHS_SHORT[tm - 1]}`, versus: 'la semana pasada', previousFrom: addDays(from, -7) };
  }
  const [y, m] = parseIso(anchorIso);
  const from = `${y}-${pad2(m)}-01`;
  const to = toIso(new Date(y, m, 0));
  const previousMonth = MONTHS[(m + 10) % 12];
  const previousFrom = toIso(new Date(y, m - 2, 1));
  return { mode, from, to, label: `${MONTHS[m - 1]} ${y}`, versus: previousMonth, previousFrom };
}

/** Ancla del período anterior (-1) o siguiente (+1). */
export function shiftAnchor(anchorIso: string, mode: PeriodMode, direction: -1 | 1): string {
  if (mode === 'week') {
    return addDays(mondayOf(anchorIso), direction * 7);
  }
  const [y, m] = parseIso(anchorIso);
  return toIso(new Date(y, m - 1 + direction, 1));
}
