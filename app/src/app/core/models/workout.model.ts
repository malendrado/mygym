/** "Memoria Viva" — bitácora cíclica de rutinas, ver WorkoutService (backend) y la memoria de
 *  proyecto mygym_memoria_viva_design para el diseño completo. */

export interface PlanDay {
  id: number;
  orderIndex: number;
  title: string;
}

export interface WorkoutPlan {
  id: number;
  name: string;
  days: PlanDay[];
}

export interface CreateWorkoutPlanRequest {
  name: string;
  dayTitles: string[];
}

export interface ExerciseLogEntry {
  exercise: string;
  notes: string | null;
}

/** suggestedPlanDayTitle/planDayTitle ya vienen resueltos desde el backend — si difieren (o
 *  planDayId es null), el socio se desvió de la sugerencia. */
export interface MemberWorkoutLog {
  id: number;
  reservationId: number;
  classDate: string | null;
  suggestedPlanDayId: number;
  suggestedPlanDayTitle: string | null;
  planDayId: number | null;
  planDayTitle: string | null;
  freeTextLabel: string | null;
  exercises: ExerciseLogEntry[] | null;
  createdAt: string;
}

/** Exactamente uno de planDayId/freeTextLabel debe venir. */
export interface SaveWorkoutLogRequest {
  planDayId: number | null;
  freeTextLabel: string | null;
  exercises: ExerciseLogEntry[];
}

/** NO_PLAN = el socio aún no tiene rutina asignada por su profesor.
 *  NO_PENDING = tiene plan, pero no hay ninguna clase asistida sin registrar.
 *  READY = hay una reserva con check-in esperando que la anote. */
export type PendingWorkoutStatus = 'NO_PLAN' | 'NO_PENDING' | 'READY';

export interface PendingWorkout {
  status: PendingWorkoutStatus;
  reservationId: number | null;
  classDate: string | null;
  plan: WorkoutPlan | null;
  suggestedPlanDayId: number | null;
}
