import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateWorkoutPlanRequest,
  MemberWorkoutLog,
  PendingWorkout,
  SaveWorkoutLogRequest,
  WorkoutPlan,
} from '../models/workout.model';

@Injectable({ providedIn: 'root' })
export class WorkoutService {
  private readonly http = inject(HttpClient);
  private readonly adminBase = `${environment.apiUrl}/api/gym-admin/members`;
  private readonly meBase = `${environment.apiUrl}/api/me/workout`;

  // ---- Lado profesor/gym-admin ----

  getActivePlan(memberId: number): Observable<WorkoutPlan | null> {
    return this.http.get<WorkoutPlan | null>(`${this.adminBase}/${memberId}/workout/plan`);
  }

  /** "Redefinir la rutina" manda esto de nuevo — desactiva el plan activo anterior (si había) y
   *  crea uno nuevo, ver WorkoutService.replacePlan en el backend. */
  replacePlan(memberId: number, payload: CreateWorkoutPlanRequest): Observable<WorkoutPlan> {
    return this.http.post<WorkoutPlan>(`${this.adminBase}/${memberId}/workout/plan`, payload);
  }

  getLatestLog(memberId: number): Observable<MemberWorkoutLog | null> {
    return this.http.get<MemberWorkoutLog | null>(`${this.adminBase}/${memberId}/workout/latest-log`);
  }

  // ---- Lado socio (self-service, pestaña "Rutina" de /member) ----

  getPending(): Observable<PendingWorkout> {
    return this.http.get<PendingWorkout>(`${this.meBase}/pending`);
  }

  createLog(reservationId: number, payload: SaveWorkoutLogRequest): Observable<MemberWorkoutLog> {
    return this.http.post<MemberWorkoutLog>(`${this.meBase}/logs/${reservationId}`, payload);
  }

  /** El registro sí es editable después de creado (decisión explícita del usuario). */
  updateLog(logId: number, payload: SaveWorkoutLogRequest): Observable<MemberWorkoutLog> {
    return this.http.put<MemberWorkoutLog>(`${this.meBase}/logs/${logId}`, payload);
  }
}
