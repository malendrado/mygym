import {
  AfterViewInit,
  Component,
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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { GymService } from '../../core/services/gym.service';
import { IonContent, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  businessOutline,
  cardOutline,
  chevronDownOutline,
  flashOutline,
  logInOutline,
  logoWhatsapp,
  mailOutline,
  notificationsOutline,
  peopleOutline,
  pulseOutline,
} from 'ionicons/icons';

addIcons({
  'business-outline': businessOutline,
  'people-outline': peopleOutline,
  'card-outline': cardOutline,
  'chevron-down-outline': chevronDownOutline,
  'flash-outline': flashOutline,
  'pulse-outline': pulseOutline,
  'notifications-outline': notificationsOutline,
  'logo-whatsapp': logoWhatsapp,
  'mail-outline': mailOutline,
  'log-in-outline': logInOutline,
});

const WHATSAPP_NUMBER = '56964641042';
const WHATSAPP_DEFAULT_MESSAGE = 'Hola! Quiero pedir una demo de mygym para mi gimnasio.';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Feature {
  title: string;
  body: string;
  span: 'large' | 'small';
  tint: 'accent' | 'surface' | 'surface-2';
}

interface PricingTier {
  name: string;
  clients: string;
  price: string;
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

  @ViewChildren('reveal') protected revealEls!: QueryList<ElementRef<HTMLElement>>;
  @ViewChild('modalCloseButton') protected modalCloseButtonRef?: ElementRef<HTMLElement>;
  @ViewChild('heroCta') protected heroCtaRef?: ElementRef<HTMLElement>;

  private observer?: IntersectionObserver;
  private lastFocusedElement: HTMLElement | null = null;
  private magneticCtaCleanup?: () => void;

  protected readonly features: Feature[] = [
    {
      title: 'Reservas y clases',
      body: 'Agenda por bloques horarios, control de aforo y lista de espera automática cuando un cupo se libera.',
      span: 'large',
      tint: 'surface',
    },
    {
      title: 'Membresías y cobros',
      body: 'Estados reales: activa, de prueba, congelada. Recordatorios automáticos antes del vencimiento.',
      span: 'large',
      tint: 'surface-2',
    },
    {
      title: 'Control de acceso',
      body: 'Torniquete, QR o huella. El acceso se corta solo si la membresía está impaga.',
      span: 'small',
      tint: 'surface',
    },
    {
      title: 'Reportes',
      body: 'Ingresos, clientes activos y qué planes funcionan mejor, sin armar una planilla a mano.',
      span: 'small',
      tint: 'accent',
    },
    {
      title: 'Pagos online',
      body: 'Webpay, Mercado Pago, transferencia. Tu socio paga como prefiera.',
      span: 'small',
      tint: 'surface-2',
    },
    {
      title: 'App para socios',
      body: 'Reservan su clase, ven su plan y pagan desde el celular, sin llamarte a ti.',
      span: 'small',
      tint: 'surface',
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
      body: 'Renuevas tu plan desde el celular, a la hora que sea, con el medio de pago que ya usas.',
    },
    {
      icon: 'pulse-outline',
      title: 'Sabes siempre cómo estás',
      body: 'Tu membresía, tus clases tomadas y cuándo vence tu plan, a la vista, sin preguntar en recepción.',
    },
    {
      icon: 'notifications-outline',
      title: 'Te avisamos antes, no después',
      body: 'Un aviso si se libera un cupo en tu clase favorita, o si tu plan está por vencer.',
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
    { label: 'Activa', tone: 'active', body: 'Al día, reserva sin restricciones.' },
    { label: 'De prueba', tone: 'trial', body: 'Acceso limitado mientras decide quedarse.' },
    { label: 'Congelada', tone: 'frozen', body: 'Pausada a pedido del socio, sin perder la antigüedad.' },
    { label: 'Impaga', tone: 'unpaid', body: 'El acceso se corta solo hasta que se regulariza.' },
  ];

  protected readonly steps: Step[] = [
    {
      icon: 'business-outline',
      title: 'Carga tu gimnasio',
      body: 'Bloques horarios, planes y sedes. Quince minutos, no una migración de meses.',
    },
    {
      icon: 'people-outline',
      title: 'Invita a tus socios',
      body: 'Cada socio arma su cuenta y ve su plan, sus clases y su historial de pagos.',
    },
    {
      icon: 'card-outline',
      title: 'Comienza a cobrar',
      body: 'Membresías, sesiones sueltas o productos, todo por los medios de pago que ya usas.',
    },
  ];

  protected readonly faqItems: FaqItem[] = [
    {
      question: '¿Tengo que migrar mis datos actuales?',
      answer:
        'Importamos tu planilla de socios y planes en la puesta en marcha, no arrancas de cero ni cargas todo a mano.',
    },
    {
      question: '¿Cuánto tarda en estar funcionando?',
      answer:
        'La carga inicial de gimnasio, planes y horarios se hace en una sesión. Tus socios pueden empezar a reservar el mismo día.',
    },
    {
      question: '¿Sirve si tengo más de una sede?',
      answer: 'Sí, cada sede tiene sus propios horarios y cupos, pero se administra todo desde una sola cuenta.',
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
      highlight: false,
      items: ['Reservas y clases', 'Membresías y cobros', 'App para socios'],
    },
    {
      name: 'Crece',
      clients: 'Hasta 400 socios activos',
      price: '$69.990',
      highlight: true,
      items: ['Todo lo de Empieza', 'Control de acceso', 'Reportes', 'Pagos online'],
    },
    {
      name: 'Escala',
      clients: 'Más de 400 socios activos',
      price: 'A medida',
      highlight: false,
      items: ['Todo lo de Crece', 'Múltiples sedes', 'Soporte prioritario'],
    },
  ];

  protected readonly whatsappUrl = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(WHATSAPP_DEFAULT_MESSAGE)}`;

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
    if (this.route.snapshot.queryParamMap.get('contacto') === 'demo-vencida') {
      this.openContact('Mi acceso a la demo de mygym venció, me gustaría agendar una reunión.');
    }
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
