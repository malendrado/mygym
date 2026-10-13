import { formatIsoDate, formatIsoRange } from './date-format';
import { shortTime } from './time-format';

describe('formatIsoDate', () => {
  it('da vuelta una fecha ISO sin correr el día', () => {
    expect(formatIsoDate('2026-10-12')).toBe('12-10-2026');
    expect(formatIsoDate('2026-01-01')).toBe('01-01-2026');
  });
  it('sin dato muestra un guion', () => {
    expect(formatIsoDate(null)).toBe('—');
  });
  it('un rango de un solo día muestra una fecha', () => {
    expect(formatIsoRange('2026-10-12', '2026-10-12')).toBe('12-10-2026');
    expect(formatIsoRange('2026-10-12', '2026-10-14')).toBe('12-10-2026 al 14-10-2026');
  });
});

describe('shortTime', () => {
  it('recorta segundos y fracciones', () => {
    expect(shortTime('18:30:00')).toBe('18:30');
    expect(shortTime('18:08:57.777')).toBe('18:08');
    expect(shortTime(null)).toBe('');
  });
});
