import {
  LIGHT_PALETTES,
  PALETTES,
  deriveSurfaceTint,
  ensureMinContrastColor,
  ensureTextDimContrast,
} from './gym-theme';

function luminance(hex: string): number {
  const channel = (start: number) => {
    const v = parseInt(hex.slice(start, start + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

function ratio(a: string, b: string): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

// Mismos valores que :root en styles.scss (tema oscuro, sin override).
const DARK_TEXT = '#eaf6f7';
const DARK_TEXT_DIM = '#a9c2c6';

describe('contraste del texto secundario por tema', () => {
  it('modo oscuro: --brand-text-dim y --brand-text cumplen 4,5:1 en las 28 paletas', () => {
    expect(PALETTES.length).toBe(28);
    for (const palette of PALETTES) {
      const { bg, card } = deriveSurfaceTint(palette.hex, 'DARK');
      for (const surface of [bg, card]) {
        expect(ratio(DARK_TEXT_DIM, surface), `${palette.key} dim`).toBeGreaterThanOrEqual(4.5);
        expect(ratio(DARK_TEXT, surface), `${palette.key} texto`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('modo claro: el texto secundario efectivo cumple 4,5:1 en todas las paletas', () => {
    expect(LIGHT_PALETTES.length).toBe(21);
    for (const palette of LIGHT_PALETTES) {
      const dim = ensureTextDimContrast(palette.mutedFg, palette.fg, [palette.bg, palette.card]);
      for (const surface of [palette.bg, palette.card]) {
        expect(ratio(dim, surface), `${palette.key} dim`).toBeGreaterThanOrEqual(4.5);
        expect(ratio(palette.fg, surface), `${palette.key} texto`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('no cambia un gris que ya cumple', () => {
    expect(ensureTextDimContrast('#475569', '#0F172A', ['#F5F9FF', '#FFFFFF'])).toBe('#475569');
  });

  it('corrige un gris que no cumple, acercándolo al texto principal', () => {
    const fixed = ensureTextDimContrast('#B0B0B0', '#000000', ['#FFFFFF']);
    expect(fixed).not.toBe('#B0B0B0');
    expect(ratio(fixed, '#FFFFFF')).toBeGreaterThanOrEqual(4.5);
  });

  it('ensureMinContrastColor sigue subiendo acentos bajos sobre superficies oscuras', () => {
    const { card } = deriveSurfaceTint('#4338CA', 'DARK');
    expect(ratio(ensureMinContrastColor('#4338CA', card), card)).toBeGreaterThanOrEqual(4.5);
  });
});
