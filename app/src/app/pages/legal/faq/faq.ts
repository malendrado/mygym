import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';
import { scrollToSection } from '../scroll-to-section';

interface FaqSection {
  title: string;
  items: { question: string; answer: string }[];
}

@Component({
  selector: 'app-legal-faq',
  imports: [RouterLink, IonContent],
  templateUrl: './faq.html',
  styleUrl: '../legal-page.scss',
})
export class LegalFaq {
  protected readonly goTo = scrollToSection;

  protected readonly sections: FaqSection[] = [
    {
      title: 'Sobre mygym',
      items: [
        {
          question: '¿Qué es mygym?',
          answer:
            'Es un software de administración para gimnasios y boxes: manejas horarios y clases, socios, planes de membresía, pagos y reservas desde un solo panel, y tus socios reservan sus clases desde su celular.',
        },
        {
          question: '¿Necesito instalar algo?',
          answer:
            'No es obligatorio. Tanto tu panel de administración como la app de tus socios funcionan desde el navegador. Instalar la app en el celular es opcional y sirve, entre otras cosas, para recibir avisos en el teléfono.',
        },
        {
          question: '¿Cómo reciben avisos en el celular mis socios?',
          answer:
            'Con la app instalada en el celular, cada socio puede activar los avisos con un toque y los recibe aunque no tenga la app abierta: cuando se libera un cupo en una clase que estaba esperando, antes de su clase (1 hora, o 30 minutos si reservó con poco tiempo), si el gimnasio cierra y cuando el cierre termina, si el gimnasio le cancela una reserva, cambia el horario de su clase o la elimina, y cuando su plan está por vencer o se lo quitan. Si el cierre es de todo el día y es para hoy o mañana, se avisa a todos los socios; si está programado con más anticipación, solo a quienes tenían clases reservadas. Los correos siguen funcionando igual. En iPhone, primero hay que agregar la app a la pantalla de inicio.',
        },
        {
          question: '¿Me avisan cuando un socio cancela una clase?',
          answer:
            'Sí. Si activas los avisos en tu celular, tú y los profesores de tu gimnasio reciben un aviso al instante cuando un socio cancela su reserva, con cuántos cupos quedan y cuántas personas esperan. Si cancelan varias personas de la misma clase, se agrupan en un solo aviso.',
        },
        {
          question: '¿Puedo personalizar mi marca?',
          answer:
            'Sí. Puedes subir tu logo, elegir tu color y personalizar la información de tu gimnasio (fotos, descripción, redes sociales, WhatsApp).',
        },
        {
          question: '¿En qué se diferencia mygym de otro software de gestión de gimnasios?',
          answer:
            'mygym está pensado para la realidad de un gimnasio en Chile: pagas en pesos chilenos, tus socios pagan con Webpay o transferencia, y el soporte es en español. Además tiene funciones pensadas para el día a día de un box: pantalla de TV en la recepción con check-in por código QR (sin comprar ningún equipo), cierre de emergencia con un clic por fuerza mayor, y una lista de espera que avisa por email y al celular apenas se libera un cupo.',
        },
        {
          question: '¿Puedo probar mygym antes de contratarlo?',
          answer:
            'Sí. Te damos acceso por 7 días a una demo: un gimnasio de ejemplo, con datos ficticios, para que recorras el panel de administración y la vista de tus socios tal como funcionan de verdad. Es de solo lectura: puedes explorar todo, pero nada de lo que hagas se guarda.',
        },
        {
          question: '¿Puedo definir hasta cuándo se puede reservar y cancelar?',
          answer:
            'Sí. Cada gimnasio define, en minutos antes de la clase, hasta cuándo se puede reservar y hasta cuándo se puede cancelar sin que la clase cuente como usada. También eliges cuánta ventaja tiene el primero de la lista de espera cuando se libera un cupo, y si tus socios pueden ver quién va a cada clase.',
        },
        {
          question: '¿Hay un límite de socios, profesores o administradores?',
          answer:
            'Sí. Según tu plan: Empieza hasta 100 socios y Crece hasta 200 (cuentan todos los socios registrados, incluidos los invitados que aún no entran). Además, cada gimnasio puede tener hasta 10 profesores y 5 administradores. En tu panel ves cuántos cupos te quedan; al llegar al límite no se pueden agregar más hasta que cambies de plan.',
        },
        {
          question: '¿Qué pasa si mi gimnasio queda en pausa?',
          answer:
            'Tus socios y tu equipo pueden entrar, pero ven un aviso tranquilo con los datos de contacto, no se puede reservar ni editar nada, la pantalla de TV muestra "Volvemos pronto" y se detienen los avisos automáticos. Tus datos se conservan y todo vuelve a la normalidad apenas el gimnasio se reactiva.',
        },
        {
          question: '¿Tengo que cargar a mano todos mis socios?',
          answer:
            'No. Puedes subir tu planilla de socios desde Excel, con su plan y las clases que ya usaron, y te mostramos fila por fila qué quedó bien y qué hay que revisar antes de confirmar la carga.',
        },
      ],
    },
    {
      title: 'Pagos y dinero',
      items: [
        {
          question: '¿Cómo pagan mis socios?',
          answer:
            'Tus socios pagan sus planes en línea a través de Flow.cl (tarjeta, Webpay y otros medios), la pasarela de pago que procesa las transacciones de forma segura, o transfiriendo directo a la cuenta bancaria de tu gimnasio, que tú confirmas con un clic. El pago es manual, mes a mes — no guardamos tarjetas ni hacemos cobros automáticos recurrentes. La comisión de la pasarela se suma al precio del plan y el socio la ve desglosada antes de pagar.',
        },
        {
          question: '¿Cuándo recibo el dinero de los pagos de mis socios?',
          answer:
            'Directo en tu propia cuenta: cada gimnasio tiene su cuenta de Flow (la creamos contigo), y los pagos de tus socios llegan ahí, sin pasar por mygym. Flow te liquida a la cuenta bancaria que registraste, según sus propios plazos. Las transferencias bancarias de tus socios llegan directo a tu cuenta. mygym no recibe ni retiene ese dinero.',
        },
        {
          question: '¿Qué pasa si un socio quiere que le devuelvan su pago?',
          answer:
            'Los reembolsos a tus socios los gestionas tú directamente como dueño del gimnasio — mygym no interviene en esa devolución. Te recomendamos tener tu propia política de reembolso clara para tus socios.',
        },
        {
          question: '¿mygym cobra alguna comisión sobre lo que pagan mis socios?',
          answer:
            'No. mygym no cobra comisión por socio ni por transacción — pagas un valor fijo mensual según el plan que corresponda a la cantidad de socios de tu gimnasio: Empieza (hasta 100 socios, $29.990/mes + IVA), Crece (hasta 200 socios, $59.990/mes + IVA) o Escala (más de 200 socios, precio a medida). Puedes subir o bajar de plan cuando lo necesites.',
        },
      ],
    },
    {
      title: 'Contrato y cancelación',
      items: [
        {
          question: '¿Por cuánto tiempo me comprometo si contrato mygym?',
          answer: 'El contrato tiene una duración mínima de 6 meses.',
        },
        {
          question: '¿Puedo arrepentirme y cancelar antes de los 6 meses?',
          answer:
            'El contrato no contempla derecho a retracto, y antes de firmar puedes probar mygym con la demo de 7 días. Lo que corresponde pagar si necesitas salir antes del plazo mínimo, y las causas por las que puedes terminar el contrato sin costo (por ejemplo, un incumplimiento de mygym), están en el contrato que firmas.',
        },
        {
          question: '¿Qué pasa si quiero dejar de usar mygym después de los 6 meses?',
          answer:
            'Cumplido el plazo mínimo, el contrato sigue mes a mes y puedes terminarlo cuando quieras, sin costo: cierras tu gimnasio dentro de la plataforma o nos avisas a contacto@mygym.cl. El término rige al final del mes ya pagado.',
        },
        {
          question: '¿Dónde quedan las condiciones completas del servicio?',
          answer:
            'En el contrato de prestación de servicios que firmas al contratar, y en la política de privacidad del sitio. El contrato incluye un anexo que regula cómo mygym trata los datos de tus socios por encargo tuyo. Esta página de preguntas frecuentes es solo informativa: si algo difiere, prevalece el contrato.',
        },
      ],
    },
    {
      title: 'Tus datos y los de tus socios',
      items: [
        {
          question: 'Si dejo mygym, ¿qué pasa con toda la información de mis socios?',
          answer:
            'Antes de cerrar tu gimnasio definitivamente, te enviamos por correo dos archivos descargables: uno con tus socios (nombre, email, plan, RUT, dirección, fecha de nacimiento, teléfono y cantidad de reservas de cada uno) y otro con el historial de pagos, para que conserves tu propia cartera de clientes. Es la copia que te entregamos: tú eres quien conserva la cartera de tus socios. Una vez confirmado el envío, eliminamos los datos de tu gimnasio de nuestros sistemas dentro de los plazos que fija el contrato; las copias de respaldo se eliminan después, al rotar según su ciclo normal.',
        },
        {
          question: '¿mygym guarda una copia de mis socios después de que me voy?',
          answer:
            'No para operar ni para otros fines. Solo conservamos un registro interno de la eliminación, con conteos (cuántos socios, pagos, reservas, etc.), y la información de tu propia empresa que la ley nos obliga a guardar, como la de facturación. Los detalles y plazos están en el contrato y en la política de privacidad.',
        },
        {
          question: '¿Qué guarda mygym cuando activo los avisos en mi teléfono?',
          answer:
            'Solo la dirección técnica que el navegador asigna a ese dispositivo para poder enviarle avisos; no incluye tu nombre, tu correo ni tu ubicación. Se borra cuando cierras sesión, cuando desactivas los avisos o cuando eliminas tu cuenta.',
        },
        {
          question: '¿Y si el envío de ese correo falla?',
          answer:
            'No se borra nada. Si por cualquier motivo no podemos confirmarte que recibiste el detalle de tus socios, la desvinculación no se ejecuta — se puede volver a intentar cuando el envío funcione.',
        },
      ],
    },
    {
      title: 'Soporte',
      items: [
        {
          question: '¿Cómo los contacto si tengo un problema?',
          answer: 'Escríbenos a contacto@mygym.cl.',
        },
      ],
    },
  ];
}
