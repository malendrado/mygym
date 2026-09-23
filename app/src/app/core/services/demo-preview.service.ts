import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BankTransferInfo, GymPhoto, MemberPlan, PublicGym } from '../models/gym.model';
import { AttendeeSummary, Member } from '../models/member.model';
import { GymBlockOccurrence, Reservation } from '../models/reservation.model';

/**
 * "Ver como socio" para un DEMO_ADMIN (ver web.routes.ts / roleGuard) — espejo de solo lectura de
 * ReservationService/GymService (/api/me/**), pero apuntado al socio de muestra fijo del gym demo
 * (ver DemoPreviewController en el backend). Nunca expone reservar/cancelar: no hay endpoints de
 * escritura acá, ni falta, porque de todas formas SecurityConfig bloquearía cualquier intento.
 */
@Injectable({ providedIn: 'root' })
export class DemoPreviewService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gym-admin/demo-preview`;

  getGym(): Observable<PublicGym> {
    return this.http.get<PublicGym>(`${this.base}/gym`);
  }

  getMembership(): Observable<Member> {
    return this.http.get<Member>(`${this.base}/membership`);
  }

  getPlans(): Observable<MemberPlan[]> {
    return this.http.get<MemberPlan[]>(`${this.base}/plans`);
  }

  listOccurrences(from: string, to: string): Observable<GymBlockOccurrence[]> {
    return this.http.get<GymBlockOccurrence[]>(`${this.base}/gym-blocks`, { params: { from, to } });
  }

  listReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.base}/reservations`);
  }

  getPhotos(): Observable<GymPhoto[]> {
    return this.http.get<GymPhoto[]>(`${this.base}/gym/photos`);
  }

  getBankTransferInfo(): Observable<BankTransferInfo> {
    return this.http.get<BankTransferInfo>(`${this.base}/gym/bank-transfer`);
  }

  getBlockAttendees(blockId: number, classDate: string): Observable<AttendeeSummary[]> {
    return this.http.get<AttendeeSummary[]>(`${this.base}/gym-blocks/${blockId}/occurrences/${classDate}/attendees`);
  }
}
