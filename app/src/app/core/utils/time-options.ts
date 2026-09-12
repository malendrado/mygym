/**
 * "HH:MM" options at 15-minute increments, covering a full day — used to
 * replace native <input type="time"> pickers (inconsistent styling across
 * browsers/OS, fiddly on touch) with a plain ion-select. Gym schedules
 * virtually always land on the hour or a quarter-hour, so a flat list beats
 * a free-form time widget here.
 */
export const TIME_OPTIONS_15MIN: string[] = Array.from({ length: 24 * 4 }, (_, i) => {
  const totalMinutes = i * 15;
  const hours = Math.floor(totalMinutes / 60)
    .toString()
    .padStart(2, '0');
  const minutes = (totalMinutes % 60).toString().padStart(2, '0');
  return `${hours}:${minutes}`;
});
