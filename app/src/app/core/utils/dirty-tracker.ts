import { effect, signal, untracked } from '@angular/core';

/**
 * Avisa (con `onChange`) si un formulario de modal tiene cambios sin guardar respecto de su estado
 * base. El padre usa ese aviso en el [canDismiss] del ion-modal para preguntar "¿Descartar los
 * cambios?" solo cuando hace falta. Llamar en contexto de inyección (inicializador de campo).
 * `markClean()` fija el estado actual como base: se llama al final de los setters de @Input que
 * cargan datos (editar un bloque/plan/rutina), así lo recién cargado no cuenta como cambio.
 */
export function trackDirty(snapshot: () => unknown, onChange: (dirty: boolean) => void): { markClean: () => void } {
  const baseline = signal<string | null>(null);
  effect(() => {
    const base = baseline();
    onChange(base !== null && JSON.stringify(snapshot()) !== base);
  });
  return { markClean: () => baseline.set(untracked(() => JSON.stringify(snapshot()))) };
}
