import { timeBandOf } from './time-band';

describe('timeBandOf', () => {
  it('corta la mañana en las 12:00 y la tarde en las 18:00', () => {
    expect(timeBandOf('00:00')).toBe('AM');
    expect(timeBandOf('11:59')).toBe('AM');
    expect(timeBandOf('12:00')).toBe('PM');
    expect(timeBandOf('17:59')).toBe('PM');
    expect(timeBandOf('18:00')).toBe('NIGHT');
    expect(timeBandOf('23:30')).toBe('NIGHT');
  });

  it('acepta la hora con segundos, como la manda el backend', () => {
    expect(timeBandOf('11:59:00')).toBe('AM');
    expect(timeBandOf('12:00:00')).toBe('PM');
    expect(timeBandOf('19:00:00')).toBe('NIGHT');
  });
});
