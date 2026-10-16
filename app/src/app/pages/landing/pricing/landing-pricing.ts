import { Component } from '@angular/core';
import { Reveal } from '../shared/reveal';

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

@Component({
  selector: 'app-landing-pricing',
  standalone: true,
  imports: [Reveal],
  templateUrl: './landing-pricing.html',
  styleUrl: './landing-pricing.scss',
})
export class LandingPricing {
  protected readonly membershipStates: MembershipState[] = [
    { label: 'Activo', tone: 'active', body: 'Plan pagado y al día: reserva sin restricciones.' },
    { label: 'Por vencer', tone: 'trial', body: 'Vence en los próximos días; le llega un recordatorio por email y al celular antes.' },
    { label: 'Vencido', tone: 'unpaid', body: 'Se le pasó la fecha: no puede reservar hasta renovar su plan.' },
    { label: 'Sin pago', tone: 'frozen', body: 'Ya tiene su cuenta, pero todavía no paga ningún plan.' },
  ];

  protected readonly pricingTiers: PricingTier[] = [
    {
      name: 'Empieza',
      clients: 'Hasta 100 socios',
      price: '$29.990',
      showIva: true,
      highlight: false,
      items: ['Reservas y clases', 'Membresías y cobros', 'App para socios'],
    },
    {
      name: 'Crece',
      clients: 'Hasta 200 socios',
      price: '$59.990',
      showIva: true,
      highlight: true,
      items: [
        'Incluye todo el plan Empieza',
        'Pagos online con Flow',
        'Pantalla de TV con check-in QR',
        'Memoria Viva',
      ],
    },
    {
      name: 'Escala',
      clients: 'Más de 200 socios',
      price: 'A medida',
      showIva: false,
      highlight: false,
      items: ['Incluye todo el plan Crece', 'Soporte prioritario', 'Funciones a la medida'],
    },
  ];
}
