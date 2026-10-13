import { BoardAttendee } from '../models/class-board.model';
import { boardCounts, classTiming, deviationText, exercisesLine, filterAttendees, santiagoWeekday, sortAttendees } from './class-board';

function att(over: Partial<BoardAttendee>): BoardAttendee {
  return {
    memberId: 1,
    name: 'Ana',
    photoUrl: null,
    phone: null,
    reservationId: 1,
    checkedInAt: null,
    checkinMethod: null,
    hasPlan: true,
    planName: 'Fuerza',
    nextDay: null,
    lastLog: null,
    thisClassLog: null,
    unloggedLast: false,
    firstClass: false,
    ...over,
  };
}

describe('tablero En clase: contadores, filtros y orden', () => {
  const list = [
    att({ memberId: 1, name: 'Zoe', checkedInAt: '2026-10-12T22:00:00Z' }),
    att({ memberId: 2, name: 'Ana', hasPlan: false }),
    att({ memberId: 3, name: 'Mario', unloggedLast: true }),
    att({ memberId: 4, name: 'Beto', firstClass: true, checkedInAt: '2026-10-12T22:01:00Z' }),
  ];

  it('cuenta reservaron, llegaron y los avisos', () => {
    expect(boardCounts(list)).toEqual({ booked: 4, arrived: 2, noPlan: 1, unlogged: 1, first: 1 });
  });
  it('filtra por cada aviso', () => {
    expect(filterAttendees(list, 'noPlan').map((a) => a.name)).toEqual(['Ana']);
    expect(filterAttendees(list, 'unlogged').map((a) => a.name)).toEqual(['Mario']);
    expect(filterAttendees(list, 'first').map((a) => a.name)).toEqual(['Beto']);
    expect(filterAttendees(list, 'all')).toHaveLength(4);
  });
  it('ordena primero los que llegaron y luego por nombre', () => {
    expect(sortAttendees(list).map((a) => a.name)).toEqual(['Beto', 'Zoe', 'Ana', 'Mario']);
  });
});

describe('textos de la tarjeta', () => {
  it('une los ejercicios con sus notas en una línea', () => {
    expect(
      exercisesLine([
        { exercise: 'Sentadilla', notes: '4x8 60 kg' },
        { exercise: 'Remo', notes: null },
      ]),
    ).toBe('Sentadilla — 4x8 60 kg · Remo');
    expect(exercisesLine(null)).toBe('');
  });
  it('dice "Le tocaba X pero hizo Y" solo si se desvió', () => {
    const base = { classDate: '2026-10-07', exercises: [] };
    expect(deviationText({ ...base, dayTitle: 'Espalda', deviated: true, doneLabel: 'cardio' })).toBe(
      'Le tocaba Espalda pero hizo cardio',
    );
    expect(deviationText({ ...base, dayTitle: 'Espalda', deviated: false, doneLabel: 'Espalda' })).toBeNull();
    expect(deviationText(null)).toBeNull();
  });
});

describe('classTiming', () => {
  // lunes 2026-10-12 19:30 en Chile (UTC-3) = 22:30 UTC
  const NOW = new Date('2026-10-12T22:30:00Z');
  const blocks = [
    { dayOfWeek: 'MONDAY' as const, startTime: '19:00:00', endTime: '20:00:00', active: true },
    { dayOfWeek: 'MONDAY' as const, startTime: '19:40:00', endTime: '20:40:00', active: true },
    { dayOfWeek: 'MONDAY' as const, startTime: '21:00:00', endTime: '22:00:00', active: true },
    { dayOfWeek: 'TUESDAY' as const, startTime: '19:00:00', endTime: '20:00:00', active: true },
    { dayOfWeek: 'MONDAY' as const, startTime: '19:00:00', endTime: '20:00:00', active: false },
  ];

  it('hoy es lunes en Chile', () => {
    expect(santiagoWeekday(NOW)).toBe('MONDAY');
  });
  it('detecta la clase en curso y la que empieza pronto', () => {
    const t = classTiming(blocks, 15, NOW);
    expect(t.live).toHaveLength(1);
    expect(t.live[0].startTime).toBe('19:00:00');
    expect(t.soon?.minutes).toBe(10);
  });
  it('sin clases cerca no hay "pronto"', () => {
    expect(classTiming(blocks, 5, NOW).soon).toBeNull();
  });
  it('ignora bloques inactivos y de otros días', () => {
    const t = classTiming([blocks[3], blocks[4]], 15, NOW);
    expect(t.live).toHaveLength(0);
    expect(t.soon).toBeNull();
  });
});
