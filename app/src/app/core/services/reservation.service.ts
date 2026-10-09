import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
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

  /** Historial de clases anteriores a hoy, de la más reciente a la más antigua, paginado. */
  myPastReservations(page: number, size: number): Observable<Page<Reservation>> {
    return this.http.get<Page<Reservation>>(`${this.base}/reservations/past`, { params: { page, size } });
  }

  book(payload: CreateReservationRequest): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.base}/reservations`, payload);
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/reservations/${id}`);
  }

  /** Canjea el QR de asistencia que muestra la TV del gym — ver /checkin/:code (checkin.ts). */
  checkIn(code: string): Observable<{ classLabels: string[] }> {
    return this.http.post<{ classLabels: string[] }>(`${this.base}/checkin`, { code });
  }

  /** Lista de espera de una clase llena — avisa por mail si se libera un cupo. */
  joinWaitlist(payload: CreateReservationRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/waitlist`, payload);
  }

  leaveWaitlist(gymBlockId: number, classDate: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/waitlist`, { params: { gymBlockId, classDate } });
  }
}
