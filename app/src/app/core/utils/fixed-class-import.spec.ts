import { normalizeText, parseDay, parseTime } from './fixed-class-import';

describe('fixed-class-import', () => {
  describe('parseDay', () => {
    it('acepta nombres completos, abreviados, con o sin tilde y en cualquier capitalización', () => {
      expect(parseDay('Lunes')).toBe('MONDAY');
      expect(parseDay('  MIÉRCOLES ')).toBe('WEDNESDAY');
      expect(parseDay('mie.')).toBe('WEDNESDAY');
      expect(parseDay('sáb')).toBe('SATURDAY');
      expect(parseDay('Dom')).toBe('SUNDAY');
    });

    it('devuelve null si no se entiende', () => {
      expect(parseDay('')).toBeNull();
      expect(parseDay('mañana')).toBeNull();
      expect(parseDay(null)).toBeNull();
    });
  });

  describe('parseTime', () => {
    it('acepta texto con dos puntos, punto o h', () => {
      expect(parseTime('19:00')).toBe('19:00');
      expect(parseTime('19.00')).toBe('19:00');
      expect(parseTime('7:30')).toBe('07:30');
      expect(parseTime('19h')).toBe('19:00');
      expect(parseTime('19:00 hrs')).toBe('19:00');
    });

    it('acepta la fracción del día que entrega Excel para una celda con formato de hora', () => {
      expect(parseTime(19 / 24)).toBe('19:00');
      expect(parseTime(0.3125)).toBe('07:30');
      expect(parseTime(0.5)).toBe('12:00');
    });

    it('toma un entero como hora en punto', () => {
      expect(parseTime(7)).toBe('07:00');
      expect(parseTime('19')).toBe('19:00');
    });

    it('rechaza horas imposibles o ilegibles', () => {
      expect(parseTime('25:00')).toBeNull();
      expect(parseTime('19:75')).toBeNull();
      expect(parseTime('tarde')).toBeNull();
      expect(parseTime(null)).toBeNull();
      expect(parseTime(30)).toBeNull();
    });
  });

  it('normalizeText ignora tildes, mayúsculas y espacios de más', () => {
    expect(normalizeText('  CrossFit  Avanzado ')).toBe('crossfit avanzado');
    expect(normalizeText('Pilates Mañana')).toBe('pilates manana');
  });
});
