import { Component, HostListener, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  ellipsisHorizontalOutline,
  helpCircleOutline,
  logInOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';
import { ContactModalService } from '../shared/contact-modal.service';

addIcons({
  'ellipsis-horizontal-outline': ellipsisHorizontalOutline,
  'help-circle-outline': helpCircleOutline,
  'log-in-outline': logInOutline,
  'shield-checkmark-outline': shieldCheckmarkOutline,
});

@Component({
  selector: 'app-landing-nav',
  standalone: true,
  imports: [IonIcon, RouterLink],
  templateUrl: './landing-nav.html',
  styleUrl: './landing-nav.scss',
})
export class LandingNav {
  protected readonly contact = inject(ContactModalService);

  // Menú "más enlaces" (FAQ/Privacidad) del header en mobile/tablet — por debajo de los
  // 900px .nav__help (los links inline de siempre) queda oculto porque no entra en el
  // ancho del header, así que se ofrece un toggle compacto con el mismo contenido en un
  // menú flotante. Reportado por el usuario: en mobile esos dos links desaparecían del
  // header por completo (seguían existiendo en el footer, pero sin ninguna señal ahí arriba).
  protected readonly helpMenuOpen = signal(false);

  protected toggleHelpMenu(): void {
    this.helpMenuOpen.update((open) => !open);
  }

  protected closeHelpMenu(): void {
    this.helpMenuOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClickForHelpMenu(event: MouseEvent): void {
    if (!this.helpMenuOpen()) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target.closest('.nav__help-wrap')) {
      this.closeHelpMenu();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.helpMenuOpen()) {
      this.closeHelpMenu();
    }
  }
}
