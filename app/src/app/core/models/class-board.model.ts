import { ExerciseLogEntry, MemberWorkoutLog } from './workout.model';

/** Tablero "En clase": lo que el profesor ve de la clase en curso y la siguiente (ver ClassBoardService en el backend). */
export interface BoardNextDay {
  id: number;
  title: string;
  order: number;
  total: number;
}

export interface BoardLastLog {
  classDate: string;
  dayTitle: string | null;
  deviated: boolean;
  doneLabel: string | null;
  exercises: ExerciseLogEntry[];
}

export interface BoardAttendee {
  memberId: number;
  name: string;
  photoUrl: string | null;
  phone: string | null;
  reservationId: number;
  checkedInAt: string | null;
  checkinMethod: 'QR' | 'MANUAL' | null;
  hasPlan: boolean;
  planName: string | null;
  nextDay: BoardNextDay | null;
  lastLog: BoardLastLog | null;
  /** El registro de ESTA clase, si ya se anotó. */
  thisClassLog: MemberWorkoutLog | null;
  /** Vino a su clase anterior y no anotó qué hizo. */
  unloggedLast: boolean;
  firstClass: boolean;
}

export interface BoardClass {
  blockId: number;
  label: string;
  category: string | null;
  instructorName: string | null;
  classDate: string;
  startTime: string;
  endTime: string;
  status: 'LIVE' | 'NEXT';
  capacity: number;
  attendees: BoardAttendee[];
}

export interface ClassBoard {
  generatedAt: string;
  classes: BoardClass[];
}
