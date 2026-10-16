import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { GymService } from '../../core/services/gym.service';
import { LandingBooking } from './booking/landing-booking';
import { LandingContactModal } from './contact-modal/landing-contact-modal';
import { LandingFaq } from './faq/landing-faq';
import { LandingFeatures } from './features/landing-features';
import { LandingFooter } from './footer/landing-footer';
import { LandingHero } from './hero/landing-hero';
import { LandingMembers } from './members/landing-members';
import { LandingNav } from './nav/landing-nav';
import { LandingPricing } from './pricing/landing-pricing';
import { ContactModalService } from './shared/contact-modal.service';
import { LandingStory } from './story/landing-story';

// Cada sección vive en su propio componente (con su SCSS): el presupuesto anyComponentStyle del
// build se mide por componente, y un solo archivo de 1.600 líneas se acercaba al límite. Lo que
// comparten (botones, tipografía, aparición al hacer scroll) está en shared/.
@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [
    IonContent,
    LandingNav,
    LandingHero,
    LandingStory,
    LandingFeatures,
    LandingMembers,
    LandingBooking,
    LandingPricing,
    LandingFaq,
    LandingFooter,
    LandingContactModal,
  ],
  providers: [ContactModalService],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private readonly gymService = inject(GymService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly contact = inject(ContactModalService);

  ngOnInit(): void {
    this.gymService.recordVisit('LANDING');
    // Suscrito (no snapshot único) — bug real encontrado en la práctica: el interceptor de
    // errores redirige acá con router.navigate (SPA, sin recargar la página) cuando el login
    // falla (410 demo vencida, 403 cuenta no registrada). Si esta misma pantalla ya estaba
    // montada antes en la sesión (ej. Landing → Entrar → login → falla → vuelve a Landing),
    // Ionic reusa la instancia del componente (mismo gotcha ya documentado para otras páginas,
    // ver ionViewWillEnter en otras partes) y ngOnInit no se vuelve a ejecutar — con un snapshot
    // único, el query param nunca se llegaba a leer y el modal de contacto no se abría, aunque
    // la URL sí mostrara el ?contacto=... correcto. queryParamMap sigue emitiendo igual.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const contacto = params.get('contacto');
      if (contacto === 'demo-vencida') {
        this.contact.open('Mi acceso a la demo de mygym venció, me gustaría agendar una reunión.');
      } else if (contacto === 'cuenta-no-registrada') {
        this.contact.open(
          'Intenté entrar a mygym con mi cuenta de Google, pero no encontré ninguna cuenta asociada. ¿Me ayudan?',
        );
      }
    });
  }
}
