import { Component, ElementRef, HostListener, effect, inject, viewChild } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { logoWhatsapp } from 'ionicons/icons';
import { ContactModalService } from '../shared/contact-modal.service';

addIcons({ 'logo-whatsapp': logoWhatsapp });

const WHATSAPP_NUMBER = '56964641042';
const WHATSAPP_DEFAULT_MESSAGE = 'Hola! Quiero pedir una demo de mygym para mi gimnasio.';

@Component({
  selector: 'app-landing-contact-modal',
  standalone: true,
  imports: [IonIcon],
  templateUrl: './landing-contact-modal.html',
  styleUrl: './landing-contact-modal.scss',
})
export class LandingContactModal {
  protected readonly contact = inject(ContactModalService);

  private readonly closeButton = viewChild<ElementRef<HTMLElement>>('modalCloseButton');

  protected readonly whatsappUrl = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(WHATSAPP_DEFAULT_MESSAGE)}`;

  constructor() {
    // Al abrir el modal y tras enviar, el foco vuelve al botón de cerrar (cuando ya existe en el DOM).
    effect(() => {
      this.contact.focusRequest();
      if (this.contact.isOpen()) {
        setTimeout(() => this.closeButton()?.nativeElement.focus());
      }
    });
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.contact.isOpen()) {
      this.contact.close();
    }
  }

  protected onModalKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }

    const card = event.currentTarget as HTMLElement;
    const focusable = card.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input, textarea, [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length === 0) {
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
