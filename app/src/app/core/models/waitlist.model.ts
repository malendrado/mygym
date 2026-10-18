/** Una entrada futura del socio en la lista de espera de una clase llena (GET /api/me/waitlist). */
export interface WaitlistEntry {
  gymBlockId: number;
  /** "YYYY-MM-DD" */
  classDate: string;
  label: string;
  /** "HH:mm:ss" o "HH:mm" */
  startTime: string;
  endTime: string;
  /** Puesto en la fila (1 = el primero en recibir el aviso). */
  position: number;
}
