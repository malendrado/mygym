/** Hora de una clase para mostrar: "18:30". El servidor la manda como "18:30:00" y, en datos cargados a
 *  mano por la API, hasta con fracciones ("18:08:57.777"): en pantalla solo importan hora y minuto. */
export function shortTime(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : '';
}
