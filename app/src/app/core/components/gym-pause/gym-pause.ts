import { Component, ElementRef, Input, OnDestroy, OnInit, computed, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { logoInstagram, logoWhatsapp, mailOutline, pauseCircleOutline } from 'ionicons/icons';
import { AuthService } from '../../services/auth.service';
import { GymSubscriptionCard } from '../gym-subscription/gym-subscription';
import { GymContact, GymPauseService } from '../../services/gym-pause.service';

addIcons({
  'pause-circle-outline': pauseCircleOutline,
  'logo-whatsapp': logoWhatsapp,
  'mail-outline': mailOutline,
  'logo-instagram': logoInstagram,
});

interface ContactLink {
  label: string;
  icon: string;
  href: string;
}

/**
 * Aviso a pantalla completa cuando el gimnasio está en pausa: todo se ve borroso y bloqueado, con un mensaje tranquilo que
 * dice qué pasa y con quién hablar. El socio ve cómo contactar a SU gimnasio (WhatsApp, correo del primer administrador,
 * Instagram); el equipo del gimnasio (admin y profesores) ve cómo contactar a mygym. El tono es a propósito sereno:
 * "pausa" y "tus datos están resguardados", nada de "desactivado" ni "bloqueado".
 */
@Component({
  selector: 'app-gym-pause',
  imports: [IonButton, IonIcon, GymSubscriptionCard],
  templateUrl: './gym-pause.html',
  styleUrl: './gym-pause.scss',
})
export class GymPause implements OnInit, OnDestroy {
  /** Quién mira: cambia el texto y a quién se le da el contacto. */
  @Input() audience: 'member' | 'staff' = 'member';

  protected readonly pause = inject(GymPauseService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  // Hermanos de la ruta del diálogo que pusimos en `inert` (para quitarlos al reanudar sin tocar los que ya lo estaban).
  private inertified: Element[] = [];
  // Vigila los contenedores de la ruta: un modal, toast o popover que se monte después (Ionic los cuelga de <ion-app>)
  // también queda inerte, no solo lo que ya existía al pausar.
  private observers: MutationObserver[] = [];
  private focusOpener: HTMLElement | null = null;

  protected readonly status = this.pause.status;
  /** El plan de mygym está sin activar o impago (y no es una pausa manual). */
  protected readonly billing = computed(() => this.pause.status()?.pauseReason === 'BILLING');
  protected readonly notStarted = computed(() => this.billing() && this.pause.status()?.billingStatus === 'NOT_STARTED');
  /** Solo el administrador puede pagar: los profesores y el modo demo solo ven el aviso. */
  protected readonly canPay = computed(() => this.audience === 'staff' && this.billing() && this.authService.currentUser()?.role === 'GYM_ADMIN');
  protected readonly contacts = computed<ContactLink[]>(() => {
    const status = this.pause.status();
    if (!status) {
      return [];
    }
    const contact = this.audience === 'staff' ? status.mygymContact : status.gymContact;
    return contact ? toLinks(contact) : [];
  });

  constructor() {
    // aria-modal por sí solo no impide que el teclado ni el lector de pantalla lleguen a la app borrosa de atrás: se deja
    // inerte todo lo que no es el aviso y el foco se lleva al título. Al reanudar se devuelve todo como estaba.
    effect(() => {
      if (this.pause.paused()) {
        this.blockBackground();
      } else {
        this.releaseBackground();
      }
    });
  }

  ngOnInit(): void {
    void this.pause.check();
    this.pause.watch();
  }

  ngOnDestroy(): void {
    this.pause.unwatch();
    this.releaseBackground();
  }

  private blockBackground(): void {
    if (this.inertified.length > 0) {
      return;
    }
    if (document.activeElement instanceof HTMLElement && document.activeElement !== document.body) {
      this.focusOpener = document.activeElement;
    }
    // El aviso vive dentro de la página (no en el router-outlet), así que se marca como inerte cada hermano a lo largo de
    // la ruta desde el aviso hasta <body>, dejando intacta la rama del propio aviso.
    let node: Element | null = this.host.nativeElement;
    // El host es display:contents: la ruta empieza en él mismo.
    while (node && node !== document.body) {
      const parent: Element | null = node.parentElement;
      if (!parent) {
        break;
      }
      const branch = node;
      for (const sibling of Array.from(parent.children)) {
        this.inertify(sibling, branch);
      }
      const observer = new MutationObserver((records) => {
        for (const record of records) {
          record.addedNodes.forEach((added) => added instanceof Element && this.inertify(added, branch));
        }
      });
      observer.observe(parent, { childList: true });
      this.observers.push(observer);
      node = parent;
    }
    // Se espera a que el @if pinte el aviso antes de enfocar el título.
    setTimeout(() => {
      (this.host.nativeElement.querySelector('#gym-pause-title') as HTMLElement | null)?.focus({ preventScroll: true });
    });
  }

  private inertify(el: Element, branch: Element): void {
    if (el !== branch && !el.hasAttribute('inert') && !/^(SCRIPT|STYLE|LINK|TEMPLATE)$/.test(el.tagName)) {
      el.setAttribute('inert', '');
      this.inertified.push(el);
    }
  }

  private releaseBackground(): void {
    this.observers.forEach((observer) => observer.disconnect());
    this.observers = [];
    if (this.inertified.length === 0) {
      return;
    }
    for (const el of this.inertified) {
      el.removeAttribute('inert');
    }
    this.inertified = [];
    const opener = this.focusOpener;
    this.focusOpener = null;
    if (opener && document.contains(opener)) {
      opener.focus({ preventScroll: true });
    }
  }

  protected async logout(): Promise<void> {
    await this.authService.logout();
    await this.router.navigate(['/login']);
  }
}

function toLinks(contact: GymContact): ContactLink[] {
  const links: ContactLink[] = [];
  const digits = (contact.whatsappNumber ?? '').replace(/\D/g, '');
  if (digits) {
    links.push({ label: 'WhatsApp', icon: 'logo-whatsapp', href: `https://wa.me/${digits}` });
  }
  if (contact.email) {
    links.push({ label: contact.email, icon: 'mail-outline', href: `mailto:${contact.email}` });
  }
  if (contact.instagramUrl && /^https?:\/\//i.test(contact.instagramUrl)) {
    links.push({ label: 'Instagram', icon: 'logo-instagram', href: contact.instagramUrl });
  }
  return links;
}
