import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AttendanceMark,
  AttendanceMembersFilter,
  AttendanceMembersPage,
  AttendanceSummary,
  MemberAttendanceDetail,
} from '../models/attendance.model';

/** Asistencia: marcado a mano y resúmenes. Sin `gymId` pega a los endpoints del gym-admin/profesor
 *  (el gym sale del JWT); con `gymId` pega a los del super-admin (gymId por path) — mismo backend. */
@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private readonly http = inject(HttpClient);
  private readonly adminBase = `${environment.apiUrl}/api/gym-admin`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  private base(gymId: number | null): string {
    return gymId === null ? this.adminBase : `${this.gymsBase}/${gymId}`;
  }

  /** Marca (true) o desmarca (false) a mano la asistencia de una reserva. */
  setAttendance(reservationId: number, attended: boolean, gymId: number | null = null): Observable<AttendanceMark> {
    return this.http.put<AttendanceMark>(`${this.base(gymId)}/reservations/${reservationId}/attendance`, { attended });
  }

  summary(from: string, to: string, previousFrom: string, gymId: number | null = null): Observable<AttendanceSummary> {
    return this.http.get<AttendanceSummary>(`${this.base(gymId)}/attendance-summary`, {
      params: { from, to, previousFrom },
    });
  }

  /** Los socios detrás de un número del resumen, paginados de a 25. */
  members(
    from: string,
    to: string,
    filter: AttendanceMembersFilter,
    page: number,
    gymId: number | null = null,
  ): Observable<AttendanceMembersPage> {
    let params = new HttpParams().set('from', from).set('to', to).set('page', page).set('size', 25);
    if (filter.blockId !== undefined) {
      params = params.set('blockId', filter.blockId);
    }
    if (filter.weekday !== undefined) {
      params = params.set('weekday', filter.weekday);
    }
    if (filter.onlyMissed) {
      params = params.set('onlyMissed', true);
    }
    return this.http.get<AttendanceMembersPage>(`${this.base(gymId)}/attendance-summary/members`, { params });
  }

  memberDetail(memberId: number, from: string, to: string, gymId: number | null = null): Observable<MemberAttendanceDetail> {
    return this.http.get<MemberAttendanceDetail>(`${this.base(gymId)}/attendance-summary/members/${memberId}`, {
      params: { from, to },
    });
  }
}
