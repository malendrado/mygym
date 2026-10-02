/** Lo crea el GYM_ADMIN para que dé clases con acceso a "Memoria Viva" — ver GymService.
 *  addProfesor/listProfesores/removeProfesor (backend) y Role.PROFESOR. */
export interface Profesor {
  id: number;
  name: string;
  email: string;
  active: boolean;
  photoUrl: string | null;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface CreateProfesorRequest {
  name: string;
  email: string;
}
