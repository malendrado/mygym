import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  FixedClass,
  FixedClassImportMemberResult,
  FixedClassImportRow,
  FixedClassRowResult,
  FixedClassesSaveResult,
} from '../models/fixed-class.model';

/** Clases fijas por socio. Sin `gymId` pega a los endpoints del gym-admin/profesor (el gym sale del
 *  JWT); con `gymId` pega a los del super-admin (gymId por path) — mismo backend, dos entradas. */
@Injectable({ providedIn: 'root' })
export class FixedClassService {
  private readonly http = inject(HttpClient);
  private readonly adminBase = `${environment.apiUrl}/api/gym-admin`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  private memberUrl(memberId: number, gymId: number | null): string {
    return gymId === null
      ? `${this.adminBase}/members/${memberId}/fixed-classes`
      : `${this.gymsBase}/${gymId}/members/${memberId}/fixed-classes`;
  }

  private importUrl(gymId: number | null): string {
    return gymId === null ? `${this.adminBase}/fixed-classes/import` : `${this.gymsBase}/${gymId}/fixed-classes/import`;
  }

  list(memberId: number, gymId: number | null = null): Observable<FixedClass[]> {
    return this.http.get<FixedClass[]>(this.memberUrl(memberId, gymId));
  }

  /** Deja al socio exactamente con estas clases (lista vacía = ninguna) y devuelve qué se reservó. */
  save(memberId: number, blockIds: number[], gymId: number | null = null): Observable<FixedClassesSaveResult> {
    return this.http.put<FixedClassesSaveResult>(this.memberUrl(memberId, gymId), { blockIds });
  }

  /** Vista previa del Excel: valida todo SIN escribir nada. Una entrada por fila, en el mismo orden. */
  validate(rows: FixedClassImportRow[], gymId: number | null = null): Observable<FixedClassRowResult[]> {
    return this.http.post<FixedClassRowResult[]>(`${this.importUrl(gymId)}/validate`, { rows });
  }

  /** Carga confirmada: mandar TODAS las filas de cada socio juntas (el archivo reemplaza su set completo). */
  importRows(rows: FixedClassImportRow[], gymId: number | null = null): Observable<FixedClassImportMemberResult[]> {
    return this.http.post<FixedClassImportMemberResult[]>(this.importUrl(gymId), { rows });
  }
}
