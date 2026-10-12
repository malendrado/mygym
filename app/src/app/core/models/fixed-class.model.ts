import { DayOfWeek } from './gym.model';

/** Una clase fija de un socio, con los datos del bloque para mostrarla sin otra consulta. */
export interface FixedClass {
  blockId: number;
  label: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  category: string | null;
  instructorName: string | null;
  /** false = el bloque se desactivó: la clase fija sigue guardada pero no genera reservas. */
  active: boolean;
}

/** Una fecha que la generación NO reservó, con el motivo (ej. "Cierre: corte de luz"). */
export interface FixedClassSkipped {
  date: string;
  label: string;
  reason: string;
}

/** Resultado de guardar las clases fijas de un socio. */
export interface FixedClassesSaveResult {
  classes: FixedClass[];
  created: number;
  skipped: FixedClassSkipped[];
  /** Reservas futuras canceladas porque se quitó su clase fija. */
  cancelled: number;
  /** true = sin plan activo: quedan guardadas y se reservan cuando pague. */
  noActivePlan: boolean;
  /** true = se cortó porque ya no le quedaban clases del plan. */
  stoppedByQuota: boolean;
}

/** Una fila del Excel ya normalizada (día y hora legibles). La clase se identifica por día + hora +
 *  NOMBRE: un gym puede tener GAP y CrossFit a la misma hora. */
export interface FixedClassImportRow {
  email: string;
  dayOfWeek: DayOfWeek;
  /** "HH:mm" */
  startTime: string;
  className: string;
}

export type FixedClassRowStatus = 'OK' | 'WARNING' | 'ERROR' | 'OMITTED';

export interface FixedClassRowResult {
  index: number;
  email: string;
  memberName: string | null;
  status: FixedClassRowStatus;
  message: string;
}

export interface FixedClassImportMemberResult {
  email: string;
  memberName: string | null;
  ok: boolean;
  message: string;
  created: number;
  skipped: number;
}
