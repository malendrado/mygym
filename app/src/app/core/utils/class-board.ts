import { BoardAttendee, BoardLastLog } from '../models/class-board.model';
import { DayOfWeek } from '../models/gym.model';
import { ExerciseLogEntry } from '../models/workout.model';
import { santiagoNow } from './attendance';

/** Reglas puras del tablero "En clase" (qué se filtra, cómo se ordena y cómo se escribe cada dato). */

export type BoardFilter = 'all' | 'noPlan' | 'unlogged' | 'first';

export interface BoardCounts {
  booked: number;
  arrived: number;
  noPlan: number;
  unlogged: number;
  first: number;
}

export function boardCounts(attendees: BoardAttendee[]): BoardCounts {
  return {
    booked: attendees.length,
    arrived: attendees.filter((a) => a.checkedInAt).length,
    noPlan: attendees.filter((a) => !a.hasPlan).length,
    unlogged: attendees.filter((a) => a.unloggedLast).length,
    first: attendees.filter((a) => a.firstClass).length,
  };
}

export function filterAttendees(attendees: BoardAttendee[], filter: BoardFilter): BoardAttendee[] {
  switch (filter) {
    case 'noPlan':
      return attendees.filter((a) => !a.hasPlan);
    case 'unlogged':
      return attendees.filter((a) => a.unloggedLast);
    case 'first':
      return attendees.filter((a) => a.firstClass);
    default:
      return attendees;
  }
}

/** Primero los que ya llegaron, después los que no; cada grupo por nombre. */
export function sortAttendees(attendees: BoardAttendee[]): BoardAttendee[] {
  return [...attendees].sort((a, b) => {
    const arrived = Number(!!b.checkedInAt) - Number(!!a.checkedInAt);
    return arrived || a.name.localeCompare(b.name, 'es');
  });
}

/** "Sentadilla — 4x8 60 kg · Remo — 3x10": los primeros ejercicios en una sola línea. */
export function exercisesLine(exercises: ExerciseLogEntry[] | null | undefined): string {
  return (exercises ?? [])
    .filter((e) => e.exercise?.trim())
    .map((e) => (e.notes?.trim() ? `${e.exercise.trim()} — ${e.notes.trim()}` : e.exercise.trim()))
    .join(' · ');
}

/** "Le tocaba Día 2 pero hizo cardio" cuando se desvió de lo sugerido; null si hizo lo que le tocaba. */
export function deviationText(log: BoardLastLog | null): string | null {
  if (!log || !log.deviated) {
    return null;
  }
  return `Le tocaba ${log.dayTitle ?? 'otro día'} pero hizo ${log.doneLabel ?? 'otra cosa'}`;
}

/** Día de la semana de HOY en Chile. */
export function santiagoWeekday(now: Date = new Date()): DayOfWeek {
  const name = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Santiago', weekday: 'long' }).format(now);
  return name.toUpperCase() as DayOfWeek;
}

export interface ScheduleBlock {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  active: boolean;
}

function minutesOf(time: string): number {
  const [h, m] = time.slice(0, 5).split(':').map(Number);
  return h * 60 + m;
}

export interface ClassTiming<T extends ScheduleBlock> {
  /** Clases que están en curso ahora. */
  live: T[];
  /** La que empieza más pronto dentro de `soonMinutes` (o null), con los minutos que faltan. */
  soon: { block: T; minutes: number } | null;
}

/**
 * Con los horarios del gym (sin llamar al servidor) dice qué clases están en curso AHORA y cuál empieza en
 * `soonMinutes` o menos. Sirve para abrir Socios directo en "En clase" y para el aviso de "tu clase empieza en N min".
 * No sabe de cierres de emergencia (el tablero sí): para esto una diferencia de un par de minutos no importa.
 */
export function classTiming<T extends ScheduleBlock>(
  blocks: T[],
  soonMinutes: number,
  now: Date = new Date(),
): ClassTiming<T> {
  const today = santiagoWeekday(now);
  const current = minutesOf(santiagoNow(now).time);
  const todays = blocks.filter((b) => b.active && b.dayOfWeek === today);
  const live = todays.filter((b) => minutesOf(b.startTime) <= current && current < minutesOf(b.endTime));
  let soon: ClassTiming<T>['soon'] = null;
  for (const b of todays) {
    const minutes = minutesOf(b.startTime) - current;
    if (minutes > 0 && minutes <= soonMinutes && (!soon || minutes < soon.minutes)) {
      soon = { block: b, minutes };
    }
  }
  return { live, soon };
}
