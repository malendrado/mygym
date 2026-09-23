import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateMemberRequest, MarkPaidRequest, Member, MemberImportRow, MemberImportRowResult } from '../models/member.model';

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gym-admin/members`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  list(): Observable<Member[]> {
    return this.http.get<Member[]>(this.base);
  }

  create(payload: CreateMemberRequest): Observable<Member> {
    return this.http.post<Member>(this.base, payload);
  }

  /** Un bloque del Excel de importación masiva — ver ImportMembersModal, que llama esto varias
   *  veces en secuencia (uno por bloque de ~20 filas), nunca el archivo entero de una sola vez. */
  importBatch(rows: MemberImportRow[]): Observable<MemberImportRowResult[]> {
    return this.http.post<MemberImportRowResult[]>(`${this.base}/import`, { rows });
  }

  /** Contraparte SUPER_ADMIN de list()/create() — mismo backend, gymId por path en vez de por JWT. */
  listForGym(gymId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.gymsBase}/${gymId}/members`);
  }

  createForGym(gymId: number, payload: CreateMemberRequest): Observable<Member> {
    return this.http.post<Member>(`${this.gymsBase}/${gymId}/members`, payload);
  }

  /** El gym-admin marca a un socio suyo como pagado. */
  markPaid(memberId: number, payload: MarkPaidRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/${memberId}/mark-paid`, payload);
  }

  /** Contraparte SUPER_ADMIN de markPaid — mismo backend, gymId por path. */
  markPaidForGym(gymId: number, memberId: number, payload: MarkPaidRequest): Observable<void> {
    return this.http.post<void>(`${this.gymsBase}/${gymId}/members/${memberId}/mark-paid`, payload);
  }

  /** El gym-admin le quita a un socio suyo el plan/pago que tenga registrado. */
  revokePlan(memberId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${memberId}/revoke-plan`, {});
  }

  /** Contraparte SUPER_ADMIN de revokePlan — mismo backend, gymId por path. */
  revokePlanForGym(gymId: number, memberId: number): Observable<void> {
    return this.http.post<void>(`${this.gymsBase}/${gymId}/members/${memberId}/revoke-plan`, {});
  }

  /** Borrado permanente de un socio — solo SUPER_ADMIN, no hay contraparte de gym-admin.
   *  Borra también todas sus reservas e historial de pagos (cascada en el backend). Irreversible. */
  deleteMemberForGym(gymId: number, memberId: number): Observable<void> {
    return this.http.delete<void>(`${this.gymsBase}/${gymId}/members/${memberId}`);
  }
}
