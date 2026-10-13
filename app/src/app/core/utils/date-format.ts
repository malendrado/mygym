/** "2026-10-12" → "12-10-2026" (mismo formato que usa el panel para las fechas con hora). Se arma con
 *  texto, nunca con `new Date(iso)`: una fecha sin hora se interpreta como medianoche UTC y en Chile
 *  se mostraría el día anterior. */
export function formatIsoDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${m[3]}-${m[2]}-${m[1]}` : iso;
}

/** Rango de fechas ISO: una sola fecha si coinciden, si no "12-10-2026 al 14-10-2026". */
export function formatIsoRange(start: string, end: string): string {
  return start === end ? formatIsoDate(start) : `${formatIsoDate(start)} al ${formatIsoDate(end)}`;
}
