import { Component } from '@angular/core';
import { Reveal } from '../shared/reveal';

interface Feature {
  title: string;
  body: string;
  // 'full' evita dejar columnas vacías cuando una tarjeta queda sola en la última fila
  // (grilla de 4 columnas): en vez de "small" (1 col, deja 3 huecas), ocupa el ancho completo.
  span: 'large' | 'small' | 'full';
  tint: 'accent' | 'surface' | 'surface-2';
}

@Component({
  selector: 'app-landing-features',
  standalone: true,
  imports: [Reveal],
  templateUrl: './landing-features.html',
  styleUrl: './landing-features.scss',
})
export class LandingFeatures {
  protected readonly features: Feature[] = [
    {
      title: 'Reservas y clases',
      body: 'Agenda por bloques horarios, cupos que se actualizan solos y lista de espera: si alguien cancela, le avisamos por email y al celular a quien estaba esperando. Tú defines hasta cuándo se puede reservar y cancelar.',
      span: 'large',
      tint: 'surface',
    },
    {
      title: 'Membresías y cobros',
      body: 'Cuatro estados claros: activo, por vencer, vencido y sin pago. Activa el plan con su fecha real de inicio y las clases que ya usó, y el socio recibe un recordatorio por email y al celular antes de que venza.',
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
      body: 'Por fuerza mayor o un problema de seguridad: cierra días completos o solo las clases que elijas (ordenadas por día y hora), devuelve el cupo y avisa por email y al celular: a quienes perdieron una clase y, si el cierre es para hoy o mañana, a todos tus socios.',
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
      body: 'Tu socio paga en línea con Flow (Webpay y otros medios) o te transfiere directo. Con Flow, el dinero llega directo a la cuenta de tu gimnasio, sin pasar por mygym; la transferencia la confirmas con un clic.',
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
      title: 'Clases fijas',
      body: 'Deja precargada la clase a la que va siempre cada socio: se le reserva sola cada semana, se le descuenta de su plan y se renueva cuando paga. Desde la ficha o con un Excel. Si quieres, solo podrán cambiarla dentro de la semana en curso.',
      span: 'small',
      tint: 'surface-2',
    },
    {
      title: 'Datos de tus socios, al día',
      body: 'RUT, dirección, fecha de nacimiento, celular y contacto de emergencia, validados: el socio los completa una sola vez al entrar. Y desde tu lista, un toque en WhatsApp para escribirle directo.',
      span: 'large',
      tint: 'surface',
    },
    {
      title: 'Asistencia: quién vino de verdad',
      body: 'Elige un día, toca una clase y ve quién reservó y quién llegó, con QR o marcado a mano. Escríbele por WhatsApp a quien faltó y revisa cada semana o mes quiénes vienen más y quiénes faltan.',
      span: 'large',
      tint: 'surface-2',
    },
    {
      title: 'Pantalla de TV',
      body: 'La clase de ahora, quién se anotó y qué viene después, siempre visible en la recepción. Se empareja con un código, nada más.',
      span: 'full',
      tint: 'accent',
    },
    {
      title: 'Memoria Viva: seguimiento de rutinas',
      body: 'El profesor arma una pauta simple y el socio anota qué hizo cada vez que entrena. Y en "En clase" ve quiénes vienen a la clase en curso y a la siguiente, cada uno con su rutina del día. Antes de hablar con el socio, el profesor ve al toque si siguió el plan o se desvió — sin tener que acordarse ni preguntar.',
      span: 'full',
      tint: 'surface-2',
    },
  ];
}
