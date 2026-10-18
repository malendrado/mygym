import { Component, ElementRef, HostListener, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  helpCircleOutline,
  logInOutline,
  menuOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';
import { scrollToSection } from '../../../core/utils/scroll-to-section';
import { ContactModalService } from '../shared/contact-modal.service';

addIcons({
  'help-circle-outline': helpCircleOutline,
  'log-in-outline': logInOutline,
  'menu-outline': menuOutline,
  'shield-checkmark-outline': shieldCheckmarkOutline,
});

@Component({
  selector: 'app-landing-nav',
  standalone: true,
  imports: [IonIcon, RouterLink],
  templateUrl: './landing-nav.html',
  styleUrl: './landing-nav.scss',
})
export class LandingNav implements OnInit, OnDestroy {
  protected readonly contact = inject(ContactModalService);

  // Menú "más enlaces" (FAQ/Privacidad) del header en mobile/tablet — por debajo de los
  // 900px .nav__help (los links inline de siempre) queda oculto porque no entra en el
  // ancho del header, así que se ofrece un toggle compacto con el mismo contenido en un
  // menú flotante. Reportado por el usuario: en mobile esos dos links desaparecían del
  // header por completo (seguían existiendo en el footer, pero sin ninguna señal ahí arriba).
  protected readonly helpMenuOpen = signal(false);

  // Sección visible según el scroll, para resaltar su enlace en la barra de escritorio.
  protected readonly activeSection = signal<string | null>(null);
  private sectionObserver?: IntersectionObserver;

  private readonly helpToggle = viewChild<ElementRef<HTMLButtonElement>>('helpToggle');

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      return;
    }
    // Sin root: mide contra el viewport, así sirve tanto si scrollea el documento como el ion-content.
    // La franja central evita que dos secciones queden activas a la vez.
    this.sectionObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.activeSection.set(entry.target.id);
          } else if (this.activeSection() === entry.target.id) {
            this.activeSection.set(null);
          }
        }
      },
      { rootMargin: '-45% 0px -50% 0px' },
    );
    // Las secciones son hermanas de este componente y pueden no estar pintadas aún.
    setTimeout(() => {
      for (const id of ['producto', 'socios', 'precios']) {
        const el = document.getElementById(id);
        if (el) {
          this.sectionObserver?.observe(el);
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.sectionObserver?.disconnect();
  }

  // Salto por ancla con scrollIntoView: el scroll vive en ion-content y el hash plano no siempre lo mueve.
  // El href se conserva para teclado, lectores de pantalla y "abrir en pestaña nueva".
  protected goTo(event: Event, id: string): void {
    scrollToSection(event, id);
    this.closeHelpMenu(false);
  }

  protected toggleHelpMenu(): void {
    this.helpMenuOpen.update((open) => !open);
  }

  // returnFocus: al cerrar con Escape o al elegir un enlace, el foco vuelve al botón "Menú"
  // (no al hacer clic fuera, donde el usuario ya eligió otro destino).
  protected closeHelpMenu(returnFocus = true): void {
    this.helpMenuOpen.set(false);
    if (returnFocus) {
      this.helpToggle()?.nativeElement.focus();
    }
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClickForHelpMenu(event: MouseEvent): void {
    if (!this.helpMenuOpen()) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target.closest('.nav__help-wrap')) {
      this.closeHelpMenu(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.helpMenuOpen()) {
      this.closeHelpMenu();
    }
  }
}
