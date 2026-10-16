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

interface FeatureGroup {
  title: string;
  items: string[];
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
      items: ['Todas las funciones incluidas', 'Hasta 10 profesores y 5 administradores', 'Sin comisión por socio ni por pago'],
    },
    {
      name: 'Crece',
      clients: 'Hasta 200 socios',
      price: '$59.990',
      showIva: true,
      highlight: true,
      items: ['Todas las funciones incluidas', 'Hasta 10 profesores y 5 administradores', 'Sin comisión por socio ni por pago'],
    },
    {
      name: 'Escala',
      clients: 'Más de 200 socios',
      price: 'A medida',
      showIva: false,
      highlight: false,
      items: ['Todas las funciones incluidas', 'Límites de equipo a tu medida', 'Soporte prioritario', 'Funciones a la medida'],
    },
  ];

  // Lo que hoy hace mygym, igual en los tres planes: el precio depende solo de cuántos socios tienes.
  protected readonly featureGroups: FeatureGroup[] = [
    {
      title: 'Reserva online',
      items: [
        'Tus socios reservan desde el celular en segundos, a cualquier hora',
        'Ven los cupos libres al instante, sin preguntarte por WhatsApp',
        'Cupos que se actualizan solos, sin recargar la app',
        'Calendario semanal de clases, navegable semana a semana',
        'Reservan y cancelan con un toque',
        'Se descuenta de su plan automáticamente',
      ],
    },
    {
      title: 'Cancelación y reglas',
      items: [
        'Cancelación online hasta el límite que tú fijas (minutos antes de la clase)',
        'Límite de reserva propio, distinto del de cancelación',
        'El cupo cancelado vuelve a quedar disponible al instante',
        'Lista de espera: el primero de la fila tiene ventaja para tomar el cupo',
        'Decide si tus socios ven quién va a cada clase',
        'Clases fijas: la de siempre, reservada sola cada semana',
      ],
    },
    {
      title: 'Cobros y pago online',
      items: [
        'Tus socios pagan en línea con Flow: tarjeta, Webpay y otros medios',
        'El dinero llega directo a la cuenta de tu gimnasio, sin pasar por mygym',
        'O transferencia directa, que confirmas con un clic',
        'Comisión de Flow transparente: el socio la ve antes de pagar',
        'Sin comisión de mygym por socio ni por pago',
        'Marca un pago manual en segundos, con su fecha real',
      ],
    },
    {
      title: 'Socios y membresías',
      items: [
        'Estado de cada socio de un vistazo: activo, por vencer, vencido o sin pago',
        'Crea planes a tu medida: mensuales, por número de clases o libres',
        'Edita o pausa un plan sin borrarlo',
        'Importa tu planilla de Excel con revisión fila por fila',
        'Ficha completa: RUT, dirección, nacimiento, celular y contacto de emergencia',
        'Buscador de socios y de clases al instante',
        'Página de alta pública con tu link, para que se inscriban solos',
        'Entran con Google o con email y contraseña',
      ],
    },
    {
      title: 'Asistencia y entrenamiento',
      items: [
        'Check-in con QR: el socio escanea y queda marcada su asistencia',
        'Pantalla de TV en recepción: clase de ahora, quién se anotó y qué viene',
        'Asistencia real: quién reservó y quién vino de verdad',
        'Consolidado por semana o mes: quiénes vienen más y quiénes faltan',
        'WhatsApp directo con quien faltó',
        'Memoria Viva: el socio anota qué hizo y el profesor lo ve antes de hablarle',
        'En clase: quién viene y qué rutina le toca a cada uno',
        'Coaching: arma el plan de entrenamiento de cada socio',
      ],
    },
    {
      title: 'Avisos automáticos',
      items: [
        'Email y notificación al celular, sin que escribas a nadie',
        'Cupo liberado para quien estaba en lista de espera',
        'Recordatorio antes de la clase y aviso de plan por vencer',
        'Aviso si cambias el horario, eliminas una clase o quitas un plan',
        'Cierre de emergencia: cancela las clases, devuelve los cupos y avisa a quien corresponde',
        'A tu equipo: cancelaciones y cupos que se liberan',
      ],
    },
    {
      title: 'Tu marca',
      items: [
        'App instalable en el celular, con tu logo y tus colores',
        'Paletas de marca y modo claro u oscuro',
        'Tu frase, descripción, Instagram y WhatsApp',
        'Hasta 8 fotos de tus instalaciones en tu página de alta',
      ],
    },
    {
      title: 'Tu equipo y tus datos',
      items: [
        'Profesores y administradores, cada uno con su acceso',
        'Pausa tu gimnasio cuando lo necesites',
        'Datos de cada gimnasio separados de los demás',
        'Si te vas, te llevas tus datos: listado de socios y pagos',
      ],
    },
  ];

}
