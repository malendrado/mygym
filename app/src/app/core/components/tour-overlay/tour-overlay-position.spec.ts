import { bottomForCardAbove } from './tour-overlay';

describe('bottomForCardAbove', () => {
  const VIEWPORT = 709;
  const CARD = 225;

  // La tarjeta ocupa [VIEWPORT - bottom - CARD, VIEWPORT - bottom]: debe quedar entera dentro de la ventana.
  const cardTop = (bottom: number) => VIEWPORT - bottom - CARD;

  it('pone la tarjeta justo encima del recorte cuando hay espacio', () => {
    const bottom = bottomForCardAbove(400, VIEWPORT, CARD);
    expect(bottom).toBe(VIEWPORT - 400 + 16);
    expect(cardTop(bottom)).toBeGreaterThanOrEqual(16);
  });

  it('con un elemento más alto que la ventana la tarjeta sigue visible (caso "Mi marca", recorte desde y=12)', () => {
    const bottom = bottomForCardAbove(12, VIEWPORT, CARD);
    expect(cardTop(bottom)).toBeGreaterThanOrEqual(0);
    expect(cardTop(bottom) + CARD).toBeLessThanOrEqual(VIEWPORT);
  });

  it('con el recorte pegado arriba tampoco se sale por el techo', () => {
    expect(cardTop(bottomForCardAbove(100, VIEWPORT, CARD))).toBeGreaterThanOrEqual(0);
  });

  it('nunca deja la tarjeta bajo el borde inferior', () => {
    expect(bottomForCardAbove(5000, VIEWPORT, CARD)).toBeGreaterThanOrEqual(16);
  });
});
