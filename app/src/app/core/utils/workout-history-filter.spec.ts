import { describe, expect, it } from 'vitest';
import { MemberWorkoutLog } from '../models/workout.model';
import {
  NO_WORKOUT_FILTER,
  OTHER_DAY,
  dayOptions,
  doneDayKey,
  filterWorkoutLogs,
  highlightParts,
  isDeviated,
  isFiltering,
  normalizeText,
} from './workout-history-filter';

function log(id: number, over: Partial<MemberWorkoutLog> = {}): MemberWorkoutLog {
  return {
    id,
    reservationId: 100 + id,
    classDate: '2026-10-14',
    suggestedPlanDayId: 10,
    suggestedPlanDayTitle: 'Día 1 - Full body',
    planDayId: 10,
    planDayTitle: 'Día 1 - Full body',
    freeTextLabel: null,
    exercises: [],
    createdAt: '2026-10-14T12:00:00Z',
    loggedByName: null,
    ...over,
  };
}

describe('workout-history-filter', () => {
  const press = log(1, { planDayId: 11, planDayTitle: 'Día 2 - Tren superior', suggestedPlanDayId: 11, exercises: [{ exercise: 'Press banca', notes: '40 kg, subir a 45' }] });
  const sentadilla = log(2, { planDayId: 12, planDayTitle: 'Día 3 - Piernas', exercises: [{ exercise: 'Sentadilla con barra', notes: null }] });
  const libre = log(3, { planDayId: null, planDayTitle: null, freeTextLabel: 'Cardio', exercises: [{ exercise: 'Trote', notes: '20 min' }] });
  const vacio = log(4);
  const all = [press, sentadilla, libre, vacio];

  it('detecta el desvío: texto libre o un día distinto del sugerido', () => {
    expect(isDeviated(vacio)).toBe(false);
    expect(isDeviated(sentadilla)).toBe(true);
    expect(isDeviated(libre)).toBe(true);
  });

  it('el texto libre cae en el grupo "Otro"', () => {
    expect(doneDayKey(libre)).toBe(OTHER_DAY);
    expect(doneDayKey(press)).toBe('Día 2 - Tren superior');
  });

  it('lista los días distintos en orden natural y deja "Otro" al final', () => {
    const d10 = log(5, { planDayId: 20, planDayTitle: 'Día 10', suggestedPlanDayId: 20 });
    const d2 = log(6, { planDayId: 21, planDayTitle: 'Día 2', suggestedPlanDayId: 21 });
    expect(dayOptions([libre, d10, d2])).toEqual(['Día 2', 'Día 10', OTHER_DAY]);
  });

  it('sin filtro devuelve todo y no cuenta como filtrando', () => {
    expect(filterWorkoutLogs(all, NO_WORKOUT_FILTER)).toEqual(all);
    expect(isFiltering(NO_WORKOUT_FILTER)).toBe(false);
    expect(isFiltering({ ...NO_WORKOUT_FILTER, query: '  ' })).toBe(false);
  });

  it('filtra por día hecho', () => {
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, day: 'Día 2 - Tren superior' })).toEqual([press]);
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, day: OTHER_DAY })).toEqual([libre]);
  });

  it('"con desvío" deja solo las clases en que hizo otra cosa', () => {
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, onlyDeviated: true })).toEqual([sentadilla, libre]);
  });

  it('busca en ejercicios y en notas, sin tildes ni mayúsculas', () => {
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, query: 'PRESS' })).toEqual([press]);
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, query: 'subir a 45' })).toEqual([press]);
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, query: 'minutos' })).toEqual([]);
    expect(filterWorkoutLogs(all, { ...NO_WORKOUT_FILTER, query: 'sentadílla' })).toEqual([sentadilla]);
  });

  it('una clase sin ejercicios nunca coincide con una búsqueda', () => {
    expect(filterWorkoutLogs([vacio], { ...NO_WORKOUT_FILTER, query: 'a' })).toEqual([]);
  });

  it('combina día, desvío y búsqueda (todas deben cumplirse)', () => {
    const f = { day: 'Día 3 - Piernas', onlyDeviated: true, query: 'barra' };
    expect(filterWorkoutLogs(all, f)).toEqual([sentadilla]);
    expect(filterWorkoutLogs(all, { ...f, query: 'trote' })).toEqual([]);
  });

  it('normalizeText quita tildes y baja a minúsculas', () => {
    expect(normalizeText('Pesó ÁÉÍ')).toBe('peso aei');
  });

  describe('highlightParts', () => {
    it('sin búsqueda devuelve el texto entero', () => {
      expect(highlightParts('Press banca', '')).toEqual([{ text: 'Press banca', match: false }]);
    });

    it('marca las coincidencias conservando el texto original', () => {
      expect(highlightParts('Press banca, press militar', 'press')).toEqual([
        { text: 'Press', match: true },
        { text: ' banca, ', match: false },
        { text: 'press', match: true },
        { text: ' militar', match: false },
      ]);
    });

    it('encuentra aunque haya tildes distintas y devuelve el tramo con su tilde original', () => {
      expect(highlightParts('Sentadílla', 'sentadilla')).toEqual([{ text: 'Sentadílla', match: true }]);
    });

    it('sin coincidencia devuelve el texto sin marcar', () => {
      expect(highlightParts('Remo', 'press')).toEqual([{ text: 'Remo', match: false }]);
    });
  });
});
