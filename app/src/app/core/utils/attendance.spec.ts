import {
  addDays,
  attendanceOf,
  attendanceEditable,
  attendanceWindowOpen,
  capacityState,
  memberAgendaPeriod,
  occurrenceStarted,
  byWorstRate,
  occurrenceEnded,
  periodOf,
  ratePct,
  rateTone,
  shiftAnchor,
  sumCounts,
  variationLabel,
} from './attendance';

// 2026-10-12 19:30 en Chile (UTC-3 en esa fecha) = 22:30 UTC.
const NOW = new Date('2026-10-12T22:30:00Z');

describe('occurrenceEnded', () => {
  it('una clase de ayer ya terminó', () => {
    expect(occurrenceEnded('2026-10-11', '23:00', NOW)).toBe(true);
  });
  it('una clase de hoy que terminó a las 19:00 ya terminó a las 19:30', () => {
    expect(occurrenceEnded('2026-10-12', '19:00', NOW)).toBe(true);
  });
  it('una clase de hoy que termina a las 20:00 sigue en curso', () => {
    expect(occurrenceEnded('2026-10-12', '20:00', NOW)).toBe(false);
  });
  it('acepta la hora con segundos', () => {
    expect(occurrenceEnded('2026-10-12', '20:00:00', NOW)).toBe(false);
  });
  it('una clase de mañana no terminó', () => {
    expect(occurrenceEnded('2026-10-13', '08:00', NOW)).toBe(false);
  });
});

describe('attendanceWindowOpen', () => {
  it('abre 10 minutos antes del inicio', () => {
    expect(attendanceWindowOpen('2026-10-12', '19:40', NOW)).toBe(true);
    expect(attendanceWindowOpen('2026-10-12', '19:41', NOW)).toBe(false);
  });
  it('una clase futura de otro día todavía no', () => {
    expect(attendanceWindowOpen('2026-10-13', '08:00', NOW)).toBe(false);
  });
});

describe('attendanceOf', () => {
  const list = [{ checkedInAt: '2026-10-12T22:04:00Z' }, { checkedInAt: null }, { checkedInAt: null }];
  it('clase terminada: los sin check-in son "no vinieron"', () => {
    expect(attendanceOf(list, true)).toEqual({ booked: 3, attended: 1, missed: 2, pending: 0 });
  });
  it('clase sin terminar: los sin check-in son "pendientes", nunca faltas', () => {
    expect(attendanceOf(list, false)).toEqual({ booked: 3, attended: 1, missed: 0, pending: 2 });
  });
  it('sin reservas', () => {
    expect(attendanceOf([], true)).toEqual({ booked: 0, attended: 0, missed: 0, pending: 0 });
  });
  it('suma varias clases', () => {
    const total = sumCounts([attendanceOf(list, true), attendanceOf(list, false)]);
    expect(total).toEqual({ booked: 6, attended: 2, missed: 2, pending: 2 });
  });
});

describe('ratePct y rateTone', () => {
  it('sin reservas no hay tasa (no "0%")', () => {
    expect(ratePct(0, 0)).toBeNull();
  });
  it('redondea', () => {
    expect(ratePct(18, 24)).toBe(75);
    expect(ratePct(2, 3)).toBe(67);
  });
  it('colores por umbral', () => {
    expect(rateTone(80)).toBe('good');
    expect(rateTone(79)).toBe('warn');
    expect(rateTone(50)).toBe('warn');
    expect(rateTone(49)).toBe('bad');
  });
});

describe('variationLabel', () => {
  it('sube, baja o igual, siempre con texto', () => {
    expect(variationLabel(75, 71, 'la semana pasada')).toBe('▲ 4 pts vs. la semana pasada');
    expect(variationLabel(70, 72, 'septiembre')).toBe('▼ 2 pts vs. septiembre');
    expect(variationLabel(70, 70, 'septiembre')).toBe('= igual que septiembre');
  });
  it('sin una de las tasas no hay comparación', () => {
    expect(variationLabel(null, 70, 'x')).toBeNull();
    expect(variationLabel(70, null, 'x')).toBeNull();
  });
});

describe('byWorstRate', () => {
  it('ordena de peor a mejor tasa y deja sin datos al final', () => {
    const rows = [
      { id: 'a', booked: 10, attended: 9 },
      { id: 'b', booked: 10, attended: 4 },
      { id: 'c', booked: 0, attended: 0 },
      { id: 'd', booked: 20, attended: 8 },
    ];
    expect([...rows].sort(byWorstRate).map((r) => r.id)).toEqual(['d', 'b', 'a', 'c']);
  });
});

describe('períodos', () => {
  it('la semana va de lunes a domingo', () => {
    const p = periodOf('2026-10-14', 'week'); // miércoles
    expect([p.from, p.to]).toEqual(['2026-10-12', '2026-10-18']);
    expect(p.label).toBe('12 oct – 18 oct');
  });
  it('un domingo pertenece a la semana que termina en él', () => {
    expect(periodOf('2026-10-18', 'week').from).toBe('2026-10-12');
  });
  it('el mes es el calendario completo (31 días máx., dentro del tope de 62 del servidor)', () => {
    const p = periodOf('2026-02-15', 'month');
    expect([p.from, p.to]).toEqual(['2026-02-01', '2026-02-28']);
    expect(p.label).toBe('febrero 2026');
    expect(p.versus).toBe('enero');
    expect(periodOf('2026-01-10', 'month').versus).toBe('diciembre');
    expect(p.previousFrom).toBe('2026-01-01');
    expect(periodOf('2026-01-10', 'month').previousFrom).toBe('2025-12-01');
    expect(periodOf('2026-10-14', 'week').previousFrom).toBe('2026-10-05');
  });
  it('navega semanas y meses, también cruzando el año', () => {
    expect(shiftAnchor('2026-10-14', 'week', -1)).toBe('2026-10-05');
    expect(shiftAnchor('2026-10-14', 'week', 1)).toBe('2026-10-19');
    expect(shiftAnchor('2026-12-20', 'month', 1)).toBe('2027-01-01');
    expect(shiftAnchor('2026-01-20', 'month', -1)).toBe('2025-12-01');
  });
  it('addDays cruza fin de mes', () => {
    expect(addDays('2026-10-30', 3)).toBe('2026-11-02');
  });
});

describe('occurrenceStarted y attendanceEditable', () => {
  it('una clase de hoy a las 19:00 ya empezó a las 19:30', () => {
    expect(occurrenceStarted('2026-10-12', '19:00', NOW)).toBe(true);
    expect(occurrenceStarted('2026-10-12', '20:00', NOW)).toBe(false);
  });
  it('se puede editar hasta 7 días después del fin y no antes de la ventana', () => {
    expect(attendanceEditable('2026-10-05', '19:00', '20:00', NOW)).toBe(true); // hace 7 días, aún antes de las 20:00 de hoy
    expect(attendanceEditable('2026-10-04', '19:00', '20:00', NOW)).toBe(false); // hace 8 días
    expect(attendanceEditable('2026-10-13', '08:00', '09:00', NOW)).toBe(false); // mañana
  });
});

describe('capacityState', () => {
  it('calcula el % lleno y marca llena solo al llegar al cupo', () => {
    expect(capacityState(0, 12)).toEqual({ fillPct: 0, full: false });
    expect(capacityState(8, 12)).toEqual({ fillPct: 67, full: false });
    expect(capacityState(12, 12)).toEqual({ fillPct: 100, full: true });
  });

  it('con sobrecupo la barra se topa en 100% y sigue llena', () => {
    expect(capacityState(14, 12)).toEqual({ fillPct: 100, full: true });
  });

  it('con capacidad 0 no divide por cero ni queda llena', () => {
    expect(capacityState(0, 0)).toEqual({ fillPct: 0, full: false });
    expect(capacityState(3, 0)).toEqual({ fillPct: 0, full: false });
  });
});

describe('memberAgendaPeriod', () => {
  it('abarca 2 semanas atrás y 6 adelante, dentro del tope de 62 días del servidor', () => {
    const p = memberAgendaPeriod('2026-10-13');
    expect(p.from).toBe('2026-09-29');
    expect(p.to).toBe('2026-11-23');
    const days = (Date.parse(p.to) - Date.parse(p.from)) / 86_400_000 + 1;
    expect(days).toBeLessThanOrEqual(62);
  });
});
