/**
 * Plantillas de los mensajes de WhatsApp que el gimnasio le manda a un socio (se pasan a whatsappUrl).
 * Puras: no leen nada del entorno. Usan solo el primer nombre del socio.
 */

/** Primer nombre del socio ("Juan Pérez" → "Juan"); vacío si no hay nombre. */
export function firstName(fullName: string | null | undefined): string {
  return (fullName ?? '').trim().split(/\s+/)[0] ?? '';
}

function greeting(name: string | null | undefined): string {
  const first = firstName(name);
  return first ? `Hola ${first}` : 'Hola';
}

/** "2026-10-17" (o "2026-10-17T10:00:00") → "17-10-2026"; si no es una fecha ISO, devuelve el texto tal cual. */
function displayDate(iso: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return match ? `${match[3]}-${match[2]}-${match[1]}` : (iso ?? '');
}

/** Plan vencido: pregunta si lo renuevan. */
export function expiredPlanMessage(name: string | null | undefined, gymName: string, endDate?: string | null): string {
  const on = endDate ? ` el ${displayDate(endDate)}` : '';
  return `${greeting(name)}, tu plan en ${gymName} venció${on}. ¿Lo renovamos?`;
}

/** Plan por vencer: aviso amable antes de la fecha. */
export function expiringPlanMessage(name: string | null | undefined, gymName: string, endDate?: string | null): string {
  const on = endDate ? ` el ${displayDate(endDate)}` : ' pronto';
  return `${greeting(name)}, tu plan en ${gymName} vence${on}. ¿Lo renovamos?`;
}

/** Faltó a una clase: lo echamos de menos. */
export function missedClassMessage(name: string | null | undefined, className?: string | null): string {
  return `${greeting(name)}, te echamos de menos en ${className?.trim() || 'el gimnasio'}. ¿Todo bien? Te esperamos en la próxima.`;
}

/** Mensaje según el estado de membresía del socio (EXPIRED / EXPIRING_SOON); null = chat sin texto. */
export function membershipMessage(
  status: string | null | undefined,
  name: string | null | undefined,
  gymName: string,
  endDate?: string | null,
): string | null {
  if (status === 'EXPIRED') {
    return expiredPlanMessage(name, gymName, endDate);
  }
  if (status === 'EXPIRING_SOON') {
    return expiringPlanMessage(name, gymName, endDate);
  }
  return null;
}

/** Mensaje del SOCIO al gimnasio: avisa que ya hizo la transferencia (con el plan elegido, si lo hay). */
export function transferNoticeMessage(
  memberName: string | null | undefined,
  gymName: string,
  planName?: string | null,
): string {
  const first = firstName(memberName);
  const who = first ? `Soy ${first}. ` : '';
  const plan = planName?.trim() ? ` del plan ${planName.trim()}` : '';
  return `Hola, ${who}ya hice la transferencia${plan} a ${gymName}. ¿Me confirman el pago?`;
}
