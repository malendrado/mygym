import { describe, expect, it } from 'vitest';
import { whatsappUrl } from './member-profile';
import { expiredPlanMessage, expiringPlanMessage, firstName, membershipMessage, missedClassMessage } from './whatsapp-messages';

describe('whatsappUrl', () => {
  it('sin mensaje mantiene el link simple', () => {
    expect(whatsappUrl('+56 9 1234 5678')).toBe('https://wa.me/56912345678');
    expect(whatsappUrl('912345678', '   ')).toBe('https://wa.me/56912345678');
  });
  it('con mensaje lo codifica en ?text=', () => {
    expect(whatsappUrl('912345678', 'Hola María, ¿qué tal?')).toBe(
      'https://wa.me/56912345678?text=' + encodeURIComponent('Hola María, ¿qué tal?'),
    );
  });
  it('sin celular válido devuelve null', () => {
    expect(whatsappUrl('123', 'Hola')).toBeNull();
    expect(whatsappUrl(null, 'Hola')).toBeNull();
  });
});

describe('plantillas de WhatsApp', () => {
  it('usa solo el primer nombre', () => {
    expect(firstName('  Juan Pérez Soto ')).toBe('Juan');
    expect(firstName(null)).toBe('');
  });
  it('plan vencido con fecha', () => {
    expect(expiredPlanMessage('Juan Pérez', 'Xalo Gym', '2026-10-05')).toBe(
      'Hola Juan, tu plan en Xalo Gym venció el 05-10-2026. ¿Lo renovamos?',
    );
  });
  it('plan vencido sin fecha ni nombre', () => {
    expect(expiredPlanMessage('', 'Xalo Gym')).toBe('Hola, tu plan en Xalo Gym venció. ¿Lo renovamos?');
  });
  it('plan por vencer', () => {
    expect(expiringPlanMessage('Ana Díaz', 'Xalo Gym', '2026-10-20')).toBe(
      'Hola Ana, tu plan en Xalo Gym vence el 20-10-2026. ¿Lo renovamos?',
    );
    expect(expiringPlanMessage('Ana', 'Xalo Gym')).toContain('vence pronto');
  });
  it('faltó a una clase', () => {
    expect(missedClassMessage('Pedro Rojas', 'Spinning 19:00')).toBe(
      'Hola Pedro, te echamos de menos en Spinning 19:00. ¿Todo bien? Te esperamos en la próxima.',
    );
  });
  it('faltó sin clase conocida', () => {
    expect(missedClassMessage('Pedro', null)).toBe(
      'Hola Pedro, te echamos de menos en el gimnasio. ¿Todo bien? Te esperamos en la próxima.',
    );
  });
  it('elige la plantilla según el estado de membresía', () => {
    expect(membershipMessage('EXPIRED', 'Luis', 'G')).toContain('venció');
    expect(membershipMessage('EXPIRING_SOON', 'Luis', 'G')).toContain('vence');
    expect(membershipMessage('ACTIVE', 'Luis', 'G')).toBeNull();
    expect(membershipMessage(undefined, 'Luis', 'G')).toBeNull();
  });
  it('el mensaje armado se codifica bien en la URL', () => {
    const url = whatsappUrl('912345678', expiredPlanMessage('Luis', 'Gym & Co', '2026-10-05'))!;
    expect(url).toContain('?text=Hola%20Luis');
    expect(url).toContain('Gym%20%26%20Co');
  });
});
