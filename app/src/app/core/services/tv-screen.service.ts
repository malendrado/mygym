import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
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
    return this.http.get<TvSchedule>(`${this.publicBase}/screens/${screenToken}/schedule`);
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
