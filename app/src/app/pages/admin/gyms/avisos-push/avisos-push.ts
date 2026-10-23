import { Component } from '@angular/core';
import { IonBackButton, IonButtons, IonContent, IonHeader, IonTitle, IonToolbar } from '@ionic/angular';

interface Aviso {
  title: string;
  body: string;
  when: string;
  urgent?: boolean;
}

interface Grupo {
  title: string;
  subtitle: string;
  avisos: Aviso[];
}

/**
 * Referencia de solo lectura para el super-admin (pedida 2026-10-20): todos los avisos push que mygym envía, con su texto y
 * cuándo sale cada uno. Es una copia a mano de PushMessages.java: si se cambia un texto allá, actualizarlo acá.
 * "urgent" = urgencia alta (llega de inmediato aunque el teléfono esté en ahorro de batería).
 */
@Component({
  selector: 'app-avisos-push',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonBackButton, IonContent],
  templateUrl: './avisos-push.html',
  styleUrl: './avisos-push.scss',
})
export class AvisosPush {
  protected readonly grupos: Grupo[] = [
    {
      title: 'Al socio · clases',
      subtitle: 'Reservas, lista de espera y cambios que hace el gimnasio.',
      avisos: [
        {
          title: '¡Se liberó un cupo!',
          body: 'Clase · día a las HH:mm. Tienes X min de ventaja.',
          when: 'Se libera un cupo y eres el primero de la lista de espera: tienes una ventana de ventaja antes de que avisemos al resto.',
          urgent: true,
        },
        {
          title: 'Todavía hay cupo en [clase]',
          body: 'Clase · día a las HH:mm. El primero que reserve se lo queda.',
          when: 'Terminó la ventaja y el cupo sigue libre. Le llega al resto de la lista de espera.',
          urgent: true,
        },
        {
          title: 'Tu clase empieza en 1 hora / 30 minutos',
          body: '[Clase] a las HH:mm en [gimnasio]',
          when: 'Un proceso corre cada 5 minutos y avisa a quien tiene reserva. Dice los minutos reales que faltan, redondeados a 5.',
          urgent: true,
        },
        {
          title: 'Cancelamos tu reserva',
          body: 'Clase · día a las HH:mm. Puedes reservar otra clase cuando quieras.',
          when: 'El admin o un profesor cancela una reserva del socio.',
        },
        {
          title: 'Cambió el horario de [clase]',
          body: 'Ahora empieza a las HH:mm (antes HH:mm) los miércoles.',
          when: 'Cambia el día o la hora de una clase en la que el socio tiene reserva.',
        },
        {
          title: '[Clase] ya no se dicta',
          body: 'Tenías reserva para miércoles 21, jueves 22 y 1 más. Reserva otra clase cuando quieras.',
          when: 'La clase se desactiva o se elimina y el socio tenía reservas.',
        },
      ],
    },
    {
      title: 'Al socio · cierres del gimnasio',
      subtitle: 'Una regla distinta según la anticipación del cierre.',
      avisos: [
        {
          title: '[Gimnasio] está cerrado / cerrará hoy, mañana…',
          body: 'Motivo del cierre.',
          when: 'Cierre para hoy o mañana: se avisa a TODOS los socios. Si ya empezó, urgencia alta.',
        },
        {
          title: '[Gimnasio] canceló tu clase / tus clases',
          body: 'Motivo: clase 1, clase 2 y X más.',
          when: 'Cierre programado con más anticipación: solo a los socios que perdieron una reserva.',
        },
        {
          title: 'El cierre de [gimnasio] terminó',
          body: 'El cierre por [motivo] ya terminó. Ahora operamos con normalidad.',
          when: 'El admin levanta un cierre antes de tiempo.',
        },
      ],
    },
    {
      title: 'Al socio · membresía',
      subtitle: 'Vencimiento, cupo de clases y plan quitado.',
      avisos: [
        {
          title: 'Tu plan vence mañana / en X días',
          body: 'Renueva antes y conservas los días que te quedan en [gimnasio].',
          when: 'Aviso previo de vencimiento.',
        },
        {
          title: 'Tu membresía vence hoy / venció',
          body: 'Renueva hoy para seguir reservando clases en [gimnasio].',
          when: 'Sale a las 09:00 del día de vencimiento. Dice "venció" solo si ya pasó la hora exacta en que vence el plan.',
        },
        {
          title: 'Usaste todas tus clases',
          body: 'No te quedan clases en este período, que termina el [fecha]. Si cancelas una reserva a tiempo, puedes usar esa clase en otra.',
          when: 'Al reservar la última clase del período (la cuota cuenta reservas, no asistencia).',
        },
        {
          title: 'Tu plan fue quitado',
          body: 'Tu plan en [gimnasio] ya no está activo y se cancelaron tus X reservas. Si es un error, habla con el gimnasio.',
          when: 'El admin le quita el plan al socio.',
        },
      ],
    },
    {
      title: 'Al equipo del gimnasio',
      subtitle: 'Administradores y profesores.',
      avisos: [
        {
          title: 'Cancelación · [clase] HH:mm',
          body: 'Fulano canceló (día). Queda 1 cupo · 2 en espera.',
          when: 'Un socio cancela su reserva. Si hay varias cancelaciones de la misma clase se agrupan en un solo aviso ("3 cancelaciones…").',
        },
      ],
    },
    {
      title: 'Al administrador · plan mygym',
      subtitle: 'La suscripción del gimnasio a mygym: aviso desde 3 días antes, 2 días de gracia y pausa al tercero.',
      avisos: [
        {
          title: 'Tu plan mygym vence mañana / en X días',
          body: 'Renueva a tiempo para que tu gimnasio siga funcionando sin interrupciones.',
          when: '3, 2 y 1 días antes del vencimiento (revisión diaria a las 09:00).',
        },
        {
          title: 'Tu plan mygym vence hoy',
          body: 'Renueva hoy para evitar que tu gimnasio se pause.',
          when: 'El día del vencimiento; el gimnasio sigue activo.',
          urgent: true,
        },
        {
          title: 'Tu plan mygym está vencido',
          body: 'Tu gimnasio se pausará mañana / en X días si no renuevas.',
          when: 'Cada día de gracia (2 días).',
          urgent: true,
        },
        {
          title: 'Tu gimnasio está en pausa',
          body: 'Renueva tu plan mygym y lo reactivamos apenas se confirme el pago.',
          when: 'Al tercer día sin pago: socios y equipo no pueden reservar ni editar. Con Flow se reactiva al instante; con transferencia, cuando el super admin la confirma.',
          urgent: true,
        },
        {
          title: 'Pago recibido, gracias',
          body: 'Tu plan mygym queda al día hasta el [fecha].',
          when: 'Se confirma un pago.',
        },
        {
          title: 'No pudimos confirmar tu transferencia',
          body: 'Revisa los datos o escríbenos a [correo de contacto de «Datos de mygym»].',
          when: 'El super admin rechaza una transferencia.',
          urgent: true,
        },
      ],
    },
    {
      title: 'Prueba',
      subtitle: 'Al activar los avisos en un teléfono.',
      avisos: [
        {
          title: 'Avisos activados ✓',
          body: 'Desde ahora te avisamos en este teléfono.',
          when: 'El usuario activa los avisos o toca "Probar". Tiene tope por hora.',
        },
      ],
    },
  ];

  protected readonly total = this.grupos.reduce((sum, g) => sum + g.avisos.length, 0);
}
