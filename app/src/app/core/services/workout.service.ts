import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateWorkoutPlanRequest,
  MemberWorkoutLog,
  PendingWorkout,
  SaveWorkoutLogRequest,
  WorkoutLogPage,
  WorkoutPlan,
} from '../models/workout.model';

@Injectable({ providedIn: 'root' })
export class WorkoutService {
  private readonly http = inject(HttpClient);
  private readonly adminBase = `${environment.apiUrl}/api/gym-admin/members`;
  private readonly meBase = `${environment.apiUrl}/api/me/workout`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  // Sin gymId: el gym sale del JWT (admin/profesor). Con gymId: super-admin, gymId por path.
  private memberBase(memberId: number, gymId: number | null): string {
    return gymId === null ? `${this.adminBase}/${memberId}/workout` : `${this.gymsBase}/${gymId}/members/${memberId}/workout`;
  }

  // ---- Lado profesor/gym-admin ----

  getActivePlan(memberId: number, gymId: number | null = null): Observable<WorkoutPlan | null> {
    return this.http.get<WorkoutPlan | null>(`${this.memberBase(memberId, gymId)}/plan`);
  }

  /** "Redefinir la rutina" manda esto de nuevo — desactiva el plan activo anterior (si había) y
   *  crea uno nuevo, ver WorkoutService.replacePlan en el backend. */
  replacePlan(memberId: number, payload: CreateWorkoutPlanRequest, gymId: number | null = null): Observable<WorkoutPlan> {
    return this.http.post<WorkoutPlan>(`${this.memberBase(memberId, gymId)}/plan`, payload);
  }

  /** Una ventana del historial del socio (7 días, el más reciente primero). Para la anterior, pasar el `nextBefore`
   *  de la respuesta como `before`. */
  getLogHistory(memberId: number, gymId: number | null = null, before: string | null = null): Observable<WorkoutLogPage> {
    return this.http.get<WorkoutLogPage>(`${this.memberBase(memberId, gymId)}/logs`, { params: this.beforeParam(before) });
  }

  private beforeParam(before: string | null): HttpParams {
    return before ? new HttpParams().set('before', before) : new HttpParams();
  }

  /** El profesor/admin anota la rutina POR el alumno (reserva con asistencia). */
  createLogForMember(
    memberId: number,
    reservationId: number,
    payload: SaveWorkoutLogRequest,
    gymId: number | null = null,
  ): Observable<MemberWorkoutLog> {
    return this.http.post<MemberWorkoutLog>(`${this.memberBase(memberId, gymId)}/logs/${reservationId}`, payload);
  }

  /** Corrige un registro ya anotado (por el socio o por otro profesor). */
  updateLogForMember(
    memberId: number,
    logId: number,
    payload: SaveWorkoutLogRequest,
    gymId: number | null = null,
  ): Observable<MemberWorkoutLog> {
    return this.http.put<MemberWorkoutLog>(`${this.memberBase(memberId, gymId)}/logs/${logId}`, payload);
  }

  getLatestLog(memberId: number): Observable<MemberWorkoutLog | null> {
    return this.http.get<MemberWorkoutLog | null>(`${this.adminBase}/${memberId}/workout/latest-log`);
  }

  // ---- Lado socio (self-service, pestaña "Rutina" de /member) ----

  getPending(): Observable<PendingWorkout> {
    return this.http.get<PendingWorkout>(`${this.meBase}/pending`);
  }

  /** Mi historial por ventanas de 7 días; ver getLogHistory. */
  getMyHistory(before: string | null = null): Observable<WorkoutLogPage> {
    return this.http.get<WorkoutLogPage>(`${this.meBase}/history`, { params: this.beforeParam(before) });
  }

  createLog(reservationId: number, payload: SaveWorkoutLogRequest): Observable<MemberWorkoutLog> {
    return this.http.post<MemberWorkoutLog>(`${this.meBase}/logs/${reservationId}`, payload);
  }

  /** El registro sí es editable después de creado (decisión explícita del usuario). */
  updateLog(logId: number, payload: SaveWorkoutLogRequest): Observable<MemberWorkoutLog> {
    return this.http.put<MemberWorkoutLog>(`${this.meBase}/logs/${logId}`, payload);
  }
}
