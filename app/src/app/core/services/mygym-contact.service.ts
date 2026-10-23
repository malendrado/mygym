import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { MygymContact } from '../models/mygym-settings.model';

/** Valores de siempre: se ven hasta que llega el dato real del servidor, y si la consulta falla. */
const FALLBACK: MygymContact = { whatsapp: '56964641042', email: 'contacto@mygym.cl' };

/**
 * Contacto de mygym (WhatsApp y correo) para toda la app: landing, FAQ, privacidad, avisos. Única fuente = el formulario «Datos de mygym»
 * del panel de super admin; el servidor lo entrega en /api/public/mygym-contact. Se consulta una vez por carga de la app.
 */
@Injectable({ providedIn: 'root' })
export class MygymContactService {
  private readonly http = inject(HttpClient);
  private readonly data = signal<MygymContact>(FALLBACK);

  readonly whatsapp = computed(() => this.data().whatsapp);
  readonly email = computed(() => this.data().email);

  constructor() {
    this.http.get<MygymContact>(`${environment.apiUrl}/api/public/mygym-contact`).subscribe({
      next: (contact) => this.set(contact),
      error: () => undefined,
    });
  }

  /** Enlace de WhatsApp hacia mygym, con un mensaje ya escrito. */
  whatsappUrl(message: string): string {
    return `https://wa.me/${this.whatsapp()}?text=${encodeURIComponent(message)}`;
  }

  set(contact: Partial<MygymContact>): void {
    this.data.set({ whatsapp: contact.whatsapp?.trim() || FALLBACK.whatsapp, email: contact.email?.trim() || FALLBACK.email });
  }
}
