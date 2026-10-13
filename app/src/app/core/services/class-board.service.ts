import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClassBoard } from '../models/class-board.model';

/** Tablero "En clase". Sin `gymId` pega al endpoint del gym-admin/profesor (el gym sale del JWT); con `gymId`, al
 *  del super-admin (gymId por path) — mismo backend, dos entradas. */
@Injectable({ providedIn: 'root' })
export class ClassBoardService {
  private readonly http = inject(HttpClient);

  get(gymId: number | null = null): Observable<ClassBoard> {
    const url =
      gymId === null
        ? `${environment.apiUrl}/api/gym-admin/class-board`
        : `${environment.apiUrl}/api/gyms/${gymId}/class-board`;
    return this.http.get<ClassBoard>(url);
  }
}
