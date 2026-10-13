import { isOutsideCurrentWeek, nextWeekMonday, weekMonday, weekSunday } from './fixed-week';

describe('semana en curso (lunes a domingo)', () => {
  it('un jueves la semana va del lunes al domingo', () => {
    expect(weekMonday('2026-10-15')).toBe('2026-10-12');
    expect(weekSunday('2026-10-15')).toBe('2026-10-18');
  });
  it('un domingo todavía es la misma semana', () => {
    expect(weekMonday('2026-10-18')).toBe('2026-10-12');
    expect(weekSunday('2026-10-18')).toBe('2026-10-18');
  });
  it('un lunes empieza la semana', () => {
    expect(weekMonday('2026-10-19')).toBe('2026-10-19');
  });
  it('cruza el fin de mes y de año', () => {
    expect(weekSunday('2026-09-30')).toBe('2026-10-04');
    expect(weekMonday('2027-01-01')).toBe('2026-12-28');
  });
  it('detecta lo que cae fuera de la semana', () => {
    const today = '2026-10-15';
    expect(isOutsideCurrentWeek('2026-10-12', today)).toBe(false);
    expect(isOutsideCurrentWeek('2026-10-18', today)).toBe(false);
    expect(isOutsideCurrentWeek('2026-10-19', today)).toBe(true);
    expect(isOutsideCurrentWeek('2026-10-11', today)).toBe(true);
  });
  it('el lunes siguiente abre la semana que viene', () => {
    expect(nextWeekMonday('2026-10-15')).toBe('2026-10-19');
  });
});
