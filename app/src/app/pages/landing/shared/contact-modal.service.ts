import { Injectable, signal } from '@angular/core';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// gtag lo carga public/ga.js solo en páginas públicas; si no está (bloqueador, otra ruta) no pasa nada.
function trackLead(method: 'form' | 'whatsapp'): void {
  (window as unknown as { gtag?: (...args: unknown[]) => void }).gtag?.('event', 'generate_lead', { method });
}

/**
 * Estado y envío del formulario de contacto. Se provee en `Landing` (no en root) para que, igual
 * que antes, el estado nazca y muera con la página: si el modal quedó abierto y se navega a otra
 * ruta, al volver no reaparece.
 */
@Injectable()
export class ContactModalService {
  readonly isOpen = signal(false);
  readonly nameError = signal<string | null>(null);
  readonly emailError = signal<string | null>(null);
  readonly isSubmitted = signal(false);
  readonly isSending = signal(false);
  readonly submitError = signal<string | null>(null);
  // Precarga del mensaje cuando llegamos acá redirigidos desde un acceso de demo vencido (ver
  // error.interceptor.ts) — el prospecto no tiene que reexplicar por qué está escribiendo.
  readonly prefilledMessage = signal('');
  // El modal pide el foco en el botón de cerrar al abrirse y tras enviar; cada cambio es un pedido.
  readonly focusRequest = signal(0);

  private lastFocusedElement: HTMLElement | null = null;

  trackWhatsapp(): void {
    trackLead('whatsapp');
  }

  open(message = ''): void {
    this.lastFocusedElement = document.activeElement as HTMLElement | null;
    this.nameError.set(null);
    this.emailError.set(null);
    this.isSubmitted.set(false);
    this.isSending.set(false);
    this.submitError.set(null);
    this.prefilledMessage.set(message);
    this.isOpen.set(true);
    this.focusRequest.update((n) => n + 1);
  }

  close(): void {
    this.isOpen.set(false);
    this.lastFocusedElement?.focus();
    this.lastFocusedElement = null;
  }

  validateName(value: string): boolean {
    const isValid = value.trim().length > 0;
    this.nameError.set(isValid ? null : 'Ingresa tu nombre.');
    return isValid;
  }

  validateEmail(value: string): boolean {
    const trimmed = value.trim();
    if (!trimmed) {
      this.emailError.set('Ingresa tu email.');
      return false;
    }
    if (!EMAIL_PATTERN.test(trimmed)) {
      this.emailError.set('Ingresa un email válido.');
      return false;
    }
    this.emailError.set(null);
    return true;
  }

  async submit(event: SubmitEvent, name: string, email: string, message: string): Promise<void> {
    event.preventDefault();

    const isNameValid = this.validateName(name);
    const isEmailValid = this.validateEmail(email);
    if (!isNameValid || !isEmailValid) {
      const form = event.target as HTMLFormElement;
      const firstInvalidId = !isNameValid ? 'contact-name' : 'contact-email';
      form.querySelector<HTMLElement>(`#${firstInvalidId}`)?.focus();
      return;
    }

    this.isSending.set(true);
    this.submitError.set(null);

    try {
      const response = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, message }),
      });

      if (!response.ok) {
        throw new Error(`Respuesta ${response.status}`);
      }

      this.isSubmitted.set(true);
      trackLead('form');
    } catch (error) {
      console.error('No se pudo enviar el formulario de contacto', error);
      this.submitError.set(
        'No pudimos enviar tu mensaje automáticamente. Prueba por WhatsApp o escríbenos directo al mail.',
      );
    } finally {
      this.isSending.set(false);
      this.focusRequest.update((n) => n + 1);
    }
  }
}
