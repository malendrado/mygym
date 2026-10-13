import { PaymentPreview } from '../models/member.model';
import { minStartDate, periodEndOf, periodText, remainingText, startDateShortcuts } from './payment-dates';

const base: PaymentPreview = {
  periodStart: '2026-09-28',
  periodEnd: '2026-10-27',
  monthlyClasses: 8,
  usedOutside: 4,
  bookedInApp: 1,
  remaining: 3,
  expiringSoon: false,
};

describe('fechas de "Marcar pago"', () => {
  it('la fecha mínima es un mes atrás más un día', () => {
    expect(minStartDate('2026-10-12')).toBe('2026-09-13');
    expect(minStartDate('2026-03-31')).toBe('2026-03-01'); // febrero no tiene 31: JS lo corre a marzo y se acota igual
  });
  it('los atajos son hoy, 1 y 2 semanas atrás', () => {
    expect(startDateShortcuts('2026-10-12')).toEqual([
      { label: 'Hoy', date: '2026-10-12' },
      { label: 'Hace 1 semana', date: '2026-10-05' },
      { label: 'Hace 2 semanas', date: '2026-09-28' },
    ]);
  });
  it('ningún atajo pasa del límite de un mes', () => {
    const min = minStartDate('2026-10-12');
    expect(startDateShortcuts('2026-10-12').every((s) => s.date >= min)).toBe(true);
  });
});

describe('textos del resumen', () => {
  it('dice desde cuándo y hasta cuándo vale', () => {
    expect(periodText(base)).toBe('Vigente del 28-09-2026 al 27-10-2026');
  });
  it('explica la cuenta de las clases que le quedan', () => {
    expect(remainingText(base)).toBe('8 del plan − 4 ya usadas − 1 reserva en la app → le quedan 3 clases');
  });
  it('singular y sin partes en cero', () => {
    expect(remainingText({ ...base, usedOutside: 0, bookedInApp: 0, remaining: 1, monthlyClasses: 8 })).toBe(
      '8 del plan → le queda 1 clase',
    );
  });
  it('si se pasó del cupo muestra cero, nunca negativo', () => {
    expect(remainingText({ ...base, remaining: -2 })).toContain('le quedan 0 clases');
  });
  it('un plan libre no tiene cuenta de clases', () => {
    expect(remainingText({ ...base, monthlyClasses: null, remaining: null })).toBeNull();
  });
});

describe('periodEndOf', () => {
  it('es un mes después menos un día', () => {
    expect(periodEndOf('2026-09-28')).toBe('2026-10-27');
    expect(periodEndOf('2026-10-12')).toBe('2026-11-11');
  });
  it('respeta los meses cortos y el cambio de año', () => {
    expect(periodEndOf('2026-01-31')).toBe('2026-02-27');
    expect(periodEndOf('2026-12-15')).toBe('2027-01-14');
  });
});
