import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Member, MemberProfile } from '../models/member.model';

/** Datos de contacto del socio (RUT, dirección, fecha de nacimiento, celular). Siempre los cuatro juntos. */
@Injectable({ providedIn: 'root' })
export class MemberProfileService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** El propio socio (pantalla obligatoria de /member). */
  updateOwn(profile: Partial<MemberProfile>): Observable<Member> {
    return this.http.put<Member>(`${this.api}/api/me/profile`, profile);
  }

  /** El admin del gym sobre un socio suyo. */
  updateForMember(memberId: number, profile: MemberProfile): Observable<Member> {
    return this.http.put<Member>(`${this.api}/api/gym-admin/members/${memberId}/profile`, profile);
  }

  /** Contraparte SUPER_ADMIN — mismo backend, gymId por path. */
  updateForGymMember(gymId: number, memberId: number, profile: MemberProfile): Observable<Member> {
    return this.http.put<Member>(`${this.api}/api/gyms/${gymId}/members/${memberId}/profile`, profile);
  }
}
