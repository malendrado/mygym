import { Component, Input, OnInit, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { logoInstagram, logoWhatsapp, mailOutline, pauseCircleOutline } from 'ionicons/icons';
import { AuthService } from '../../services/auth.service';
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
  imports: [IonButton, IonIcon],
  templateUrl: './gym-pause.html',
  styleUrl: './gym-pause.scss',
})
export class GymPause implements OnInit {
  /** Quién mira: cambia el texto y a quién se le da el contacto. */
  @Input() audience: 'member' | 'staff' = 'member';

  protected readonly pause = inject(GymPauseService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly status = this.pause.status;
  protected readonly contacts = computed<ContactLink[]>(() => {
    const status = this.pause.status();
    if (!status) {
      return [];
    }
    const contact = this.audience === 'staff' ? status.mygymContact : status.gymContact;
    return contact ? toLinks(contact) : [];
  });

  ngOnInit(): void {
    void this.pause.check();
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
