/** Hue (0-360) of a #rrggbb color, ignoring saturation/lightness. */
function hexToHue(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;
  if (delta === 0) {
    return 0;
  }
  let hue: number;
  if (max === r) hue = ((g - b) / delta) % 6;
  else if (max === g) hue = (b - r) / delta + 2;
  else hue = (r - g) / delta + 4;
  hue *= 60;
  return hue < 0 ? hue + 360 : hue;
}

function hslToHex(h: number, s: number, l: number): string {
  const sFrac = s / 100;
  const lFrac = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const a = sFrac * Math.min(lFrac, 1 - lFrac);
  const f = (n: number) => lFrac - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  const toHex = (x: number) =>
    Math.round(x * 255)
      .toString(16)
      .padStart(2, '0');
  return `#${toHex(f(0))}${toHex(f(8))}${toHex(f(4))}`;
}

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  return {
    r: parseInt(hex.slice(1, 3), 16),
    g: parseInt(hex.slice(3, 5), 16),
    b: parseInt(hex.slice(5, 7), 16),
  };
}

function relativeLuminance({ r, g, b }: { r: number; g: number; b: number }): number {
  const linear = (c: number) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

function contrastRatio(hexA: string, hexB: string): number {
  const lA = relativeLuminance(hexToRgb(hexA));
  const lB = relativeLuminance(hexToRgb(hexB));
  const [lighter, darker] = lA > lB ? [lA, lB] : [lB, lA];
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Un gym puede elegir cualquier color como acento (color libre) — algunos,
 * usados directamente como texto sobre las superficies oscuras de la app,
 * no alcanzan 4.5:1 (ej. el índigo real de Fortis mide ~4.07:1 contra su
 * propia tarjeta). Se usa SOLO donde el acento pinta texto plano, nunca
 * donde pinta un fondo sólido — ahí sigue el color exacto que eligió el
 * admin, para no diluir "tu color exacto" en toda la superficie de marca.
 */
export function ensureMinContrastColor(accentHex: string, backgroundHex: string, minRatio = 4.5): string {
  if (contrastRatio(accentHex, backgroundHex) >= minRatio) {
    return accentHex;
  }
  const hue = hexToHue(accentHex);
  const bgIsDark = relativeLuminance(hexToRgb(backgroundHex)) < 0.5;
  // Sobre fondo oscuro hay que ACLARAR el acento (subir L); sobre fondo claro, oscurecerlo.
  for (let l = bgIsDark ? 55 : 45; bgIsDark ? l <= 95 : l >= 5; l += bgIsDark ? 5 : -5) {
    const candidate = hslToHex(hue, 85, l);
    if (contrastRatio(candidate, backgroundHex) >= minRatio) {
      return candidate;
    }
  }
  return bgIsDark ? '#ffffff' : '#000000';
}

/**
 * Derives a subtle panel background + card tone from a gym's accent color,
 * matching the same darkness/saturation as the app's own brand ink (#1e2c30
 * is ~H200 S23 L15) so every gym feels like a variation of the same product,
 * not an unrelated color scheme. Shared by the gym-admin panel and the public
 * per-gym join page so both derive the same look from the same accent.
 */
export function deriveSurfaceTint(accentHex: string): { bg: string; card: string } {
  const hue = hexToHue(accentHex);
  return { bg: hslToHex(hue, 22, 15), card: hslToHex(hue, 22, 19) };
}
