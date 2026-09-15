import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateMemberRequest, Member } from '../models/member.model';

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

  /** Contraparte SUPER_ADMIN de list()/create() — mismo backend, gymId por path en vez de por JWT. */
  listForGym(gymId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.gymsBase}/${gymId}/members`);
  }

  createForGym(gymId: number, payload: CreateMemberRequest): Observable<Member> {
    return this.http.post<Member>(`${this.gymsBase}/${gymId}/members`, payload);
  }
}
