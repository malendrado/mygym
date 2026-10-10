import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateProfesorRequest, Profesor } from '../models/profesor.model';

@Injectable({ providedIn: 'root' })
export class ProfesorService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gym-admin/profesores`;
  private readonly gymsBase = `${environment.apiUrl}/api/gyms`;

  list(): Observable<Profesor[]> {
    return this.http.get<Profesor[]>(this.base);
  }

  create(payload: CreateProfesorRequest): Observable<Profesor> {
    return this.http.post<Profesor>(this.base, payload);
  }

  remove(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${userId}`);
  }

  /** Contraparte SUPER_ADMIN de list/create/remove — mismo backend, gymId por path en vez de por JWT. */
  listForGym(gymId: number): Observable<Profesor[]> {
    return this.http.get<Profesor[]>(`${this.gymsBase}/${gymId}/profesores`);
  }

  createForGym(gymId: number, payload: CreateProfesorRequest): Observable<Profesor> {
    return this.http.post<Profesor>(`${this.gymsBase}/${gymId}/profesores`, payload);
  }

  removeForGym(gymId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.gymsBase}/${gymId}/profesores/${userId}`);
  }
}
