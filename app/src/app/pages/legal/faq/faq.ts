import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent } from '@ionic/angular';

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
          answer: 'No. Tanto tu panel de administración como la app de tus socios funcionan desde el navegador, sin instalar nada.',
        },
        {
          question: '¿Puedo personalizar mi marca?',
          answer:
            'Sí. Puedes subir tu logo, elegir tu color y personalizar la información de tu gimnasio (fotos, descripción, redes sociales, WhatsApp).',
        },
      ],
    },
    {
      title: 'Pagos y dinero',
      items: [
        {
          question: '¿Cómo pagan mis socios?',
          answer:
            'Tus socios pagan sus planes en línea a través de Flow.cl, la pasarela de pago que procesa las transacciones de forma segura. El pago es manual, mes a mes — no guardamos tarjetas ni hacemos cobros automáticos recurrentes.',
        },
        {
          question: '¿Cuándo recibo el dinero de los pagos de mis socios?',
          answer:
            'Cuando Flow.cl consolida (liquida) los pagos recibidos, mygym te transfiere el mismo día el monto correspondiente a tu gimnasio, a la cuenta bancaria que nos indiques.',
        },
        {
          question: '¿Qué pasa si un socio quiere que le devuelvan su pago?',
          answer:
            'Los reembolsos a tus socios los gestionas tú directamente como dueño del gimnasio — mygym no interviene en esa devolución. Te recomendamos tener tu propia política de reembolso clara para tus socios.',
        },
        {
          question: '¿mygym cobra alguna comisión sobre lo que pagan mis socios?',
          answer:
            'No. mygym no cobra comisión por socio ni por transacción — pagas un valor fijo mensual según el plan que corresponda a la cantidad de socios activos de tu gimnasio: Empieza (hasta 100 socios, $39.990/mes), Crece (hasta 400 socios, $69.990/mes) o Escala (más de 400 socios, precio a medida). Puedes subir o bajar de plan cuando lo necesites.',
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
            'No — el contrato no tiene derecho a retracto. Si necesitas dejar de usar mygym, puedes hacerlo al cumplirse el plazo mínimo simplemente no renovando el contrato.',
        },
        {
          question: '¿Qué pasa si quiero dejar de usar mygym después de los 6 meses?',
          answer: 'Cierras tu gimnasio dentro de la plataforma y el contrato no se renueva. No hay ningún trámite de cancelación adicional de tu parte.',
        },
      ],
    },
    {
      title: 'Tus datos y los de tus socios',
      items: [
        {
          question: 'Si dejo mygym, ¿qué pasa con toda la información de mis socios?',
          answer:
            'Antes de cerrar tu gimnasio definitivamente, te enviamos por correo el detalle completo de tus socios (nombre, email, plan, historial de pagos y de reservas) en archivos descargables, para que conserves tu propia cartera de clientes. Es tu única copia — mygym no se queda con ninguna. Recién después de que ese correo se envía correctamente se eliminan todos los datos de tu gimnasio de nuestros sistemas de forma permanente e irreversible.',
        },
        {
          question: '¿mygym guarda una copia de mis socios después de que me voy?',
          answer:
            'No. Solo queda un registro interno con conteos (cuántos socios, pagos, reservas, etc.) como prueba de que el borrado ocurrió — sin ningún dato personal de tus socios.',
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
