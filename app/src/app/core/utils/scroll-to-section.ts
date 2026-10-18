// Salta a una sección de una página legal desde el índice. Se usa scrollIntoView desde un
// handler (y no href="#id") porque el scroll vive dentro de ion-content (shadow DOM de Ionic)
// y los enlaces con hash no lo mueven de forma confiable.
export function scrollToSection(event: Event, id: string): void {
  event.preventDefault();
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.getElementById(id)?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'start' });
}
