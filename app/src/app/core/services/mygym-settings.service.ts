import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MygymSettings, MygymSettingsRequest } from '../models/mygym-settings.model';
import { MygymContactService } from './mygym-contact.service';

/** Datos de mygym como empresa: los lee y guarda el super-admin. Al guardar, el contacto público se actualiza en esta misma pantalla. */
@Injectable({ providedIn: 'root' })
export class MygymSettingsService {
  private readonly http = inject(HttpClient);
  private readonly contact = inject(MygymContactService);
  private readonly url = `${environment.apiUrl}/api/mygym-settings`;

  get(): Observable<MygymSettings> {
    return this.http.get<MygymSettings>(this.url);
  }

  save(request: MygymSettingsRequest): Observable<MygymSettings> {
    return this.http.put<MygymSettings>(this.url, request).pipe(tap((saved) => this.contact.set({ whatsapp: saved.whatsapp, email: saved.contactEmail })));
  }
}
