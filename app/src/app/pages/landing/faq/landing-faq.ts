import { Component, signal } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { businessOutline, cardOutline, chevronDownOutline, peopleOutline } from 'ionicons/icons';
import { Reveal } from '../shared/reveal';

addIcons({
  'business-outline': businessOutline,
  'card-outline': cardOutline,
  'chevron-down-outline': chevronDownOutline,
  'people-outline': peopleOutline,
});

interface Step {
  icon: string;
  title: string;
  body: string;
}

interface FaqItem {
  question: string;
  answer: string;
}

@Component({
  selector: 'app-landing-faq',
  standalone: true,
  imports: [IonIcon, Reveal],
  templateUrl: './landing-faq.html',
  styleUrl: './landing-faq.scss',
})
export class LandingFaq {
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
}
