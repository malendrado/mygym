import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateProfesorRequest, Profesor } from '../models/profesor.model';

@Injectable({ providedIn: 'root' })
export class ProfesorService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gym-admin/profesores`;

  list(): Observable<Profesor[]> {
    return this.http.get<Profesor[]>(this.base);
  }

  create(payload: CreateProfesorRequest): Observable<Profesor> {
    return this.http.post<Profesor>(this.base, payload);
  }

  remove(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${userId}`);
  }
}
