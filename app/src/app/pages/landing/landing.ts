import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  QueryList,
  ViewChild,
  ViewChildren,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { GymService } from '../../core/services/gym.service';
import { IonContent, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  arrowForwardOutline,
  barbellOutline,
  businessOutline,
  cardOutline,
  chevronDownOutline,
  ellipsisHorizontalOutline,
  flashOutline,
  helpCircleOutline,
  logInOutline,
  logoWhatsapp,
  mailOutline,
  notificationsOutline,
  peopleOutline,
  pulseOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';

addIcons({
  'business-outline': businessOutline,
  'people-outline': peopleOutline,
  'card-outline': cardOutline,
  'chevron-down-outline': chevronDownOutline,
  'ellipsis-horizontal-outline': ellipsisHorizontalOutline,
  'flash-outline': flashOutline,
  'pulse-outline': pulseOutline,
  'notifications-outline': notificationsOutline,
  'logo-whatsapp': logoWhatsapp,
  'mail-outline': mailOutline,
  'log-in-outline': logInOutline,
  'help-circle-outline': helpCircleOutline,
  'shield-checkmark-outline': shieldCheckmarkOutline,
  'arrow-forward-outline': arrowForwardOutline,
  'barbell-outline': barbellOutline,
});

const WHATSAPP_NUMBER = '56964641042';
const WHATSAPP_DEFAULT_MESSAGE = 'Hola! Quiero pedir una demo de mygym para mi gimnasio.';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Feature {
  title: string;
  body: string;
  // 'full' evita dejar columnas vacías cuando una tarjeta queda sola en la última fila
  // (grilla de 4 columnas): en vez de "small" (1 col, deja 3 huecas), ocupa el ancho completo.
  span: 'large' | 'small' | 'full';
  tint: 'accent' | 'surface' | 'surface-2';
}

interface PricingTier {
  name: string;
  clients: string;
  price: string;
  // false solo para "Escala" (precio a medida, no un número) — el impuesto se negocia junto con
  // el precio, no tiene sentido mostrar "+ IVA" al lado de "A medida".
  showIva: boolean;
  highlight: boolean;
  items: string[];
}

interface MembershipState {
  label: string;
  tone: 'active' | 'trial' | 'frozen' | 'unpaid';
  body: string;
}

interface Attendee {
  initials: string;
  name: string;
}

interface Step {
  icon: string;
  title: string;
  body: string;
}

interface FaqItem {
  question: string;
  answer: string;
}

interface MemberBenefit {
  icon: string;
  title: string;
  body: string;
}

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [IonContent, IonIcon, RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit, AfterViewInit, OnDestroy {
  private readonly gymService = inject(GymService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChildren('reveal') protected revealEls!: QueryList<ElementRef<HTMLElement>>;
  @ViewChild('modalCloseButton') protected modalCloseButtonRef?: ElementRef<HTMLElement>;
  @ViewChild('heroCta') protected heroCtaRef?: ElementRef<HTMLElement>;

  private observer?: IntersectionObserver;
  private lastFocusedElement: HTMLElement | null = null;
  private magneticCtaCleanup?: () => void;

  protected readonly features: Feature[] = [
    {
      title: 'Reservas y clases',
      body: 'Agenda por bloques horarios, control de cupos y lista de espera: si alguien cancela, le avisamos por email a quien estaba esperando.',
      span: 'large',
      tint: 'surface',
    },
    {
      title: 'Membresías y cobros',
      body: 'Cuatro estados claros: activo, por vencer, vencido y sin pago. Recordatorio automático por email antes de que venza el plan.',
      span: 'large',
      tint: 'surface-2',
    },
    {
      title: 'Check-in con QR',
      body: 'La TV de la recepción muestra un QR que se renueva solo; el socio lo escanea y queda marcada su asistencia. Sin plan al día, no puede reservar.',
      span: 'small',
      tint: 'surface',
    },
    {
      title: 'Cierre de emergencia',
      body: 'Por fuerza mayor o un problema de seguridad: cancela las clases afectadas, devuelve el cupo y avisa a cada socio por email, todo en un clic.',
      span: 'small',
      tint: 'surface-2',
    },
    {
      title: 'Importa tu planilla',
      body: 'Sube tu Excel de socios con su plan y las clases que ya usaron. Te mostramos fila por fila qué quedó bien y qué revisar.',
      span: 'small',
      tint: 'accent',
    },
    {
      title: 'Pagos online',
      body: 'Tu socio paga en línea con Flow (Webpay y otros medios) o te transfiere directo; la transferencia la confirmas con un clic.',
      span: 'small',
      tint: 'surface-2',
    },
    {
      title: 'App para socios',
      body: 'Se instala en el celular como una app. Entran con Google o con email y contraseña, reservan, ven su plan y pagan sin llamarte.',
      span: 'small',
      tint: 'surface',
    },
    {
      title: 'Pantalla de TV',
      body: 'La clase de ahora, quién se anotó y qué viene después, siempre visible en la recepción. Se empareja con un código, nada más.',
      span: 'full',
      tint: 'accent',
    },
    {
      title: 'Memoria Viva: seguimiento de rutinas',
      body: 'El profesor arma una pauta simple y el socio anota qué hizo cada vez que entrena. Antes de hablar con él, el profesor ve al toque si siguió el plan o se desvió — sin tener que acordarse ni preguntar.',
      span: 'full',
      tint: 'surface-2',
    },
  ];

  protected readonly memberBenefits: MemberBenefit[] = [
    {
      icon: 'flash-outline',
      title: 'Reserva en 10 segundos',
      body: 'Ves los cupos libres al toque y confirmas. Sin escribir por WhatsApp a preguntar si queda lugar.',
    },
    {
      icon: 'card-outline',
      title: 'Paga cuando quieras',
      body: 'Renuevas tu plan desde el celular, a la hora que sea: en línea o por transferencia.',
    },
    {
      icon: 'pulse-outline',
      title: 'Sabes siempre cómo estás',
      body: 'Tu membresía, tus clases tomadas y cuándo vence tu plan, a la vista, sin preguntar en recepción.',
    },
    {
      icon: 'notifications-outline',
      title: 'Te avisamos antes, no después',
      body: 'Un aviso si se libera un cupo en la clase que estabas esperando, o si tu plan está por vencer.',
    },
    {
      icon: 'barbell-outline',
      title: 'Tu rutina, siempre a mano',
      body: 'Anota qué hiciste en cada clase — tu profesor lo ve antes de hablar contigo.',
    },
  ];

  protected readonly classTime = '18:30 - 20:00';
  protected readonly classCapacity = { taken: 7, total: 10 };
  protected readonly attendees: Attendee[] = [
    { initials: 'CP', name: 'Constanza Pérez' },
    { initials: 'DG', name: 'Dayana Guerra' },
    { initials: 'JP', name: 'Javiera Pulgar' },
    { initials: 'MU', name: 'Makarena Ubeda' },
  ];

  protected readonly membershipStates: MembershipState[] = [
    { label: 'Activo', tone: 'active', body: 'Plan pagado y al día: reserva sin restricciones.' },
    { label: 'Por vencer', tone: 'trial', body: 'Vence en los próximos días; le llega un recordatorio por email antes.' },
    { label: 'Vencido', tone: 'unpaid', body: 'Se le pasó la fecha: no puede reservar hasta renovar su plan.' },
    { label: 'Sin pago', tone: 'frozen', body: 'Ya tiene su cuenta, pero todavía no paga ningún plan.' },
  ];

  protected readonly steps: Step[] = [
    {
      icon: 'business-outline',
      title: 'Carga tu gimnasio',
      body: 'Bloques horarios, planes, tu logo y tus colores. Quince minutos, no una migración de meses.',
    },
    {
      icon: 'people-outline',
      title: 'Invita a tus socios',
      body: 'Agrégalos a mano, súbelos desde Excel o comparte tu link. Entran con Google o con email y contraseña.',
    },
    {
      icon: 'card-outline',
      title: 'Comienza a cobrar',
      body: 'Tus socios pagan su plan mensual en línea o por transferencia, y tú ves quién está al día.',
    },
  ];

  protected readonly faqItems: FaqItem[] = [
    {
      question: '¿Tengo que migrar mis datos actuales?',
      answer:
        'Puedes subir tu planilla de socios desde Excel, con su plan y las clases que ya usaron. No arrancas de cero ni cargas todo a mano.',
    },
    {
      question: '¿Cuánto tarda en estar funcionando?',
      answer:
        'La carga inicial de gimnasio, planes y horarios se hace en una sesión. Tus socios pueden empezar a reservar el mismo día.',
    },
    {
      question: '¿Mis socios necesitan una cuenta de Google?',
      answer:
        'No. Pueden entrar con su cuenta de Google o crear su cuenta con email y contraseña; si la olvidan, la recuperan desde su correo.',
    },
    {
      question: '¿Puedo cambiar de plan o cancelar?',
      answer: 'Puedes subir o bajar de plan cuando quieras. El contrato mínimo es de 6 meses.',
    },
  ];

  protected readonly openFaqIndexes = signal<ReadonlySet<number>>(new Set());

  protected toggleFaq(index: number): void {
    this.openFaqIndexes.update((current) => {
      const next = new Set(current);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  protected readonly pricingTiers: PricingTier[] = [
    {
      name: 'Empieza',
      clients: 'Hasta 100 socios activos',
      price: '$39.990',
      showIva: true,
      highlight: false,
      items: ['Reservas y clases', 'Membresías y cobros', 'App para socios'],
    },
    {
      name: 'Crece',
      clients: 'Hasta 400 socios activos',
      price: '$69.990',
      showIva: true,
      highlight: true,
      items: ['Todo lo de Empieza', 'Pagos online con Flow', 'Pantalla de TV con check-in QR', 'Memoria Viva'],
    },
    {
      name: 'Escala',
      clients: 'Más de 400 socios activos',
      price: 'A medida',
      showIva: false,
      highlight: false,
      items: ['Todo lo de Crece', 'Soporte prioritario', 'Funciones a la medida'],
    },
  ];

  protected readonly whatsappUrl = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(WHATSAPP_DEFAULT_MESSAGE)}`;

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

  protected readonly isContactOpen = signal(false);
  protected readonly nameError = signal<string | null>(null);
  protected readonly emailError = signal<string | null>(null);
  protected readonly isSubmitted = signal(false);
  protected readonly isSending = signal(false);
  protected readonly submitError = signal<string | null>(null);
  // Precarga del mensaje cuando llegamos acá redirigidos desde un acceso de demo vencido (ver
  // error.interceptor.ts) — el prospecto no tiene que reexplicar por qué está escribiendo.
  protected readonly prefilledMessage = signal('');

  protected openContact(message = ''): void {
    this.lastFocusedElement = document.activeElement as HTMLElement | null;
    this.nameError.set(null);
    this.emailError.set(null);
    this.isSubmitted.set(false);
    this.isSending.set(false);
    this.submitError.set(null);
    this.prefilledMessage.set(message);
    this.isContactOpen.set(true);
    setTimeout(() => this.modalCloseButtonRef?.nativeElement.focus());
  }

  protected closeContact(): void {
    this.isContactOpen.set(false);
    this.lastFocusedElement?.focus();
    this.lastFocusedElement = null;
  }

  protected validateName(value: string): boolean {
    const isValid = value.trim().length > 0;
    this.nameError.set(isValid ? null : 'Ingresa tu nombre.');
    return isValid;
  }

  protected validateEmail(value: string): boolean {
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

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.isContactOpen()) {
      this.closeContact();
    }
    if (this.helpMenuOpen()) {
      this.closeHelpMenu();
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

  protected async submitContact(event: SubmitEvent, name: string, email: string, message: string): Promise<void> {
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
    } catch (error) {
      console.error('No se pudo enviar el formulario de contacto', error);
      this.submitError.set(
        'No pudimos enviar tu mensaje automáticamente. Prueba por WhatsApp o escríbenos directo al mail.',
      );
    } finally {
      this.isSending.set(false);
      setTimeout(() => this.modalCloseButtonRef?.nativeElement.focus());
    }
  }

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
        this.openContact('Mi acceso a la demo de mygym venció, me gustaría agendar una reunión.');
      } else if (contacto === 'cuenta-no-registrada') {
        this.openContact(
          'Intenté entrar a mygym con mi cuenta de Google, pero no encontré ninguna cuenta asociada. ¿Me ayudan?',
        );
      }
    });
  }

  ngAfterViewInit(): void {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      return;
    }

    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            this.observer?.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.2 },
    );

    this.revealEls.forEach((el) => this.observer?.observe(el.nativeElement));
    this.setupMagneticCta();
  }

  private setupMagneticCta(): void {
    const button = this.heroCtaRef?.nativeElement;
    if (!button) {
      return;
    }

    const onPointerMove = (event: PointerEvent): void => {
      const rect = button.getBoundingClientRect();
      const x = (event.clientX - rect.left - rect.width / 2) * 0.3;
      const y = (event.clientY - rect.top - rect.height / 2) * 0.3;
      button.style.transform = `translate(${x}px, ${y}px)`;
    };

    const onPointerLeave = (): void => {
      button.style.transform = '';
    };

    button.addEventListener('pointermove', onPointerMove);
    button.addEventListener('pointerleave', onPointerLeave);
    this.magneticCtaCleanup = () => {
      button.removeEventListener('pointermove', onPointerMove);
      button.removeEventListener('pointerleave', onPointerLeave);
    };
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.magneticCtaCleanup?.();
  }
}
