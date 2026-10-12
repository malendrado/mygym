import { addressError, birthDateError, formatMobile, normalizeMobile, parseBirthDateCell, whatsappUrl } from './member-profile';

describe('member-profile', () => {
  it('normaliza el celular chileno a formato internacional', () => {
    expect(normalizeMobile('9 1234 5678')).toBe('+56912345678');
    expect(normalizeMobile('+56 9 1234 5678')).toBe('+56912345678');
    expect(normalizeMobile('56912345678')).toBe('+56912345678');
    expect(normalizeMobile('(9) 1234-5678')).toBe('+56912345678');
  });

  it('rechaza fijos y números mal formados', () => {
    for (const bad of ['221234567', '+56 2 2123 4567', '12345', '', null, '+1 415 555 2671']) {
      expect(normalizeMobile(bad)).toBeNull();
    }
  });

  it('formatea el celular para mostrarlo y arma el link de WhatsApp', () => {
    expect(formatMobile('+56912345678')).toBe('+56 9 1234 5678');
    expect(formatMobile('hola')).toBe('hola');
    expect(whatsappUrl('+56912345678')).toBe('https://wa.me/56912345678');
    expect(whatsappUrl('221234567')).toBeNull();
    expect(whatsappUrl(null)).toBeNull();
  });

  it('valida la fecha de nacimiento: no futura y con edad plausible', () => {
    const year = new Date().getFullYear();
    expect(birthDateError(`${year - 30}-05-20`)).toBeNull();
    expect(birthDateError(`${year + 1}-01-01`)).toContain('futura');
    expect(birthDateError(`${year - 5}-01-01`)).toContain('edad');
    expect(birthDateError(`${year - 120}-01-01`)).toContain('edad');
    expect(birthDateError('')).not.toBeNull();
    expect(birthDateError('20/05/1990')).not.toBeNull();
  });

  it('valida el largo de la dirección', () => {
    expect(addressError('Av. Siempre Viva 742')).toBeNull();
    expect(addressError('abc')).not.toBeNull();
    expect(addressError('x'.repeat(201))).not.toBeNull();
  });

  it('lee fechas de Excel en varios formatos', () => {
    expect(parseBirthDateCell('20-05-1990')).toBe('1990-05-20');
    expect(parseBirthDateCell('5/1/1990')).toBe('1990-01-05');
    expect(parseBirthDateCell('1990-05-20')).toBe('1990-05-20');
    expect(parseBirthDateCell(33013)).toBe('1990-05-20'); // número de serie de Excel
    expect(parseBirthDateCell('31-02-1990')).toBeNull();
    expect(parseBirthDateCell('ayer')).toBeNull();
    expect(parseBirthDateCell(null)).toBeNull();
  });
});
