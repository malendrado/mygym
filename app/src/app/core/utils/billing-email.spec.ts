import { describe, expect, it } from 'vitest';
import { isValidBillingEmail, normalizeBillingEmail } from './billing-email';

describe('correo de la factura', () => {
  it('accepts normal addresses, with spaces and capitals around them', () => {
    expect(isValidBillingEmail('facturas@gimnasio.cl')).toBe(true);
    expect(isValidBillingEmail('  Juan.Perez+pagos@Mi-Gimnasio.com.cl ')).toBe(true);
    expect(normalizeBillingEmail('  Facturas@Gimnasio.CL ')).toBe('facturas@gimnasio.cl');
  });

  it('refuses addresses that cannot receive mail', () => {
    for (const bad of ['', 'sin-arroba.cl', 'juan@gimnasio', 'a@b.c', 'a b@gimnasio.cl', 'juan@@gimnasio.cl', 'juan..perez@gimnasio.cl', 'juan@gimnasio..cl', '@gimnasio.cl']) {
      expect(isValidBillingEmail(bad), bad).toBe(false);
    }
  });
});
