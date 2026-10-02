import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  TvBlockOccurrence,
  TvCheckinCode,
  TvPairingCreated,
  TvPairingStatus,
  TvSchedule,
  TvScreen,
  TvScreenClaimRequest,
} from '../models/tv-screen.model';

@Injectable({ providedIn: 'root' })
export class TvScreenService {
  private readonly http = inject(HttpClient);
  private readonly publicBase = `${environment.apiUrl}/api/public/tv`;
  private readonly adminBase = `${environment.apiUrl}/api/gym-admin/tv-screens`;

  // --- Público, sin auth — usado por la pantalla de TV misma (tv-screen.ts) ---

  createPairing(): Observable<TvPairingCreated> {
    return this.http.post<TvPairingCreated>(`${this.publicBase}/pairing`, {});
  }

  pairingStatus(code: string): Observable<TvPairingStatus> {
    return this.http.get<TvPairingStatus>(`${this.publicBase}/pairing/${code}`);
  }

  schedule(screenToken: string): Observable<TvSchedule> {
    return this.http
      .get<TvSchedule>(`${this.publicBase}/screens/${screenToken}/schedule`)
      .pipe(map((sched) => this.withAttendeesGroupedByPlan(sched)));
  }

  // Agrupa a los asistentes de cada roster por plan (mismo orden que la leyenda global —
  // planId ascendente, ver planLegend en tv-screen.ts) — pedido explícito del usuario para
  // poder comparar los colores de un vistazo en vez de verlos salpicados. Dentro de cada plan
  // se mantiene el orden alfabético que ya manda el backend (sort estable).
  private withAttendeesGroupedByPlan(schedule: TvSchedule): TvSchedule {
    const grouped = (occ: TvBlockOccurrence): TvBlockOccurrence => ({
      ...occ,
      attendees: [...occ.attendees].sort((a, b) => (a.planId ?? Infinity) - (b.planId ?? Infinity)),
    });
    return {
      ...schedule,
      current: schedule.current.map(grouped),
      next: schedule.next.map(grouped),
      later: schedule.later.map(grouped),
      previous: schedule.previous ? grouped(schedule.previous) : null,
    };
  }

  checkinCode(screenToken: string): Observable<TvCheckinCode> {
    return this.http.post<TvCheckinCode>(`${this.publicBase}/screens/${screenToken}/checkin-code`, {});
  }

  // --- Gym-admin, autenticado — usado por el panel (gym-admin.ts) ---

  listMyScreens(): Observable<TvScreen[]> {
    return this.http.get<TvScreen[]>(this.adminBase);
  }

  claimScreen(payload: TvScreenClaimRequest): Observable<TvScreen> {
    return this.http.post<TvScreen>(this.adminBase, payload);
  }

  removeScreen(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminBase}/${id}`);
  }
}
