import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateReservationRequest, GymBlockOccurrence, Reservation } from '../models/reservation.model';

@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/me`;

  listOccurrences(from: string, to: string): Observable<GymBlockOccurrence[]> {
    return this.http.get<GymBlockOccurrence[]>(`${this.base}/gym-blocks`, { params: { from, to } });
  }

  myReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.base}/reservations`);
  }

  book(payload: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.base}/reservations`, payload);
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/reservations/${id}`);
  }
}
