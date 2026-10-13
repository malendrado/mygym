// Franjas horarias para filtrar clases (Mañana / Tarde / Noche). Los cortes (12:00 y 18:00) son un criterio de negocio
// simple, no vienen de ninguna configuración del gimnasio — mismo criterio que el filtro de Horarios en gym-admin.

export type TimeBand = 'AM' | 'PM' | 'NIGHT';

export const TIME_BAND_OPTIONS: { value: TimeBand; label: string; hint: string }[] = [
  { value: 'AM', label: 'Mañana', hint: 'Clases que empiezan antes de las 12:00' },
  { value: 'PM', label: 'Tarde', hint: 'Clases que empiezan entre las 12:00 y las 18:00' },
  { value: 'NIGHT', label: 'Noche', hint: 'Clases que empiezan desde las 18:00' },
];

export const TIME_BAND_ALL_HINT = 'Mostrar clases de cualquier horario';

/** Franja de una hora de inicio ("07:00" o "07:00:00"). */
export function timeBandOf(startTime: string): TimeBand {
  const time = startTime.slice(0, 5);
  if (time < '12:00') {
    return 'AM';
  }
  return time < '18:00' ? 'PM' : 'NIGHT';
}
