import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateMemberRequest,
  MarkPaidRequest,
  Member,
  MemberImportRow,
  MemberImportRowResult,
  MemberPageQuery,
  MemberSummary,
} from '../models/member.model';
import { Page } from '../models/page.model';

// Solo manda los filtros que tienen valor — el servidor trata ausente = sin filtro.
function pageParams(query: MemberPageQuery): Record<string, string | number> {
  const params: Record<string, string | number> = { page: query.page, size: query.size };
  if (query.q) {
    params['q'] = query.q;
  }
  if (query.status) {
    params['status'] = query.status;
  }
  if (query.invite) {
    params['invite'] = query.invite;
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gym-admin/members`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  /** Una página de socios — búsqueda y filtros de estado/invitación los resuelve el servidor. */
  listPage(query: MemberPageQuery): Observable<Page<Member>> {
    return this.http.get<Page<Member>>(this.base, { params: pageParams(query) });
  }

  /** Conteos de las calugas (gym completo, no la página visible). */
  summary(): Observable<MemberSummary> {
    return this.http.get<MemberSummary>(`${this.base}/summary`);
  }

  /** Emails de todos los socios — solo para el chequeo de duplicados del modal de importación. */
  emails(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/emails`);
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
  listPageForGym(gymId: number, query: MemberPageQuery): Observable<Page<Member>> {
    return this.http.get<Page<Member>>(`${this.gymsBase}/${gymId}/members`, { params: pageParams(query) });
  }

  summaryForGym(gymId: number): Observable<MemberSummary> {
    return this.http.get<MemberSummary>(`${this.gymsBase}/${gymId}/members/summary`);
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

  /** El gym-admin borra permanentemente a un socio suyo — pedido explícito del usuario (antes
   *  solo lo tenía SUPER_ADMIN). Borra también todas sus reservas e historial de pagos (cascada
   *  en el backend). Irreversible. */
  deleteMember(memberId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${memberId}`);
  }

  /** Contraparte SUPER_ADMIN de deleteMember — mismo backend, gymId por path. */
  deleteMemberForGym(gymId: number, memberId: number): Observable<void> {
    return this.http.delete<void>(`${this.gymsBase}/${gymId}/members/${memberId}`);
  }
}
