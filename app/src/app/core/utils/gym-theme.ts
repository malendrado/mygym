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
