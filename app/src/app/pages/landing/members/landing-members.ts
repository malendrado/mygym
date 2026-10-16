import { Component } from '@angular/core';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alarmOutline,
  barbellOutline,
  cardOutline,
  flashOutline,
  notificationsOutline,
  pulseOutline,
  repeatOutline,
} from 'ionicons/icons';
import { Reveal } from '../shared/reveal';

addIcons({
  'alarm-outline': alarmOutline,
  'barbell-outline': barbellOutline,
  'card-outline': cardOutline,
  'flash-outline': flashOutline,
  'notifications-outline': notificationsOutline,
  'pulse-outline': pulseOutline,
  'repeat-outline': repeatOutline,
});

interface MemberBenefit {
  icon: string;
  title: string;
  body: string;
}

@Component({
  selector: 'app-landing-members',
  standalone: true,
  imports: [IonIcon, Reveal],
  templateUrl: './landing-members.html',
  styleUrl: './landing-members.scss',
})
export class LandingMembers {
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
      body: 'Un aviso en tu celular si se libera un cupo en la clase que estabas esperando, antes de tu clase, si el gimnasio cierra o cambia tu clase, o si tu plan está por vencer.',
    },
    {
      icon: 'repeat-outline',
      title: 'Tu clase de siempre, ya reservada',
      body: 'Si entrenas los mismos días, tu gimnasio te deja la clase fija: aparece reservada cada semana y, si un día no vas, la cancelas con un toque.',
    },
    {
      icon: 'barbell-outline',
      title: 'Tu rutina, siempre a mano',
      body: 'Anota qué hiciste en cada clase — tu profesor lo ve antes de hablar contigo.',
    },
  ];
}
