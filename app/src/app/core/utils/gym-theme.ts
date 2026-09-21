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

// Mismo formato que Gym.themeMode en el backend ('DARK'/'LIGHT') — se usa tal cual en toda
// la cadena (modelo → componentes → estas utilidades) para no andar convirtiendo casing.
export type ThemeMode = 'DARK' | 'LIGHT';

export interface LightPaletteEntry {
  key: string;
  label: string;
  hex: string;
  contrast: string;
  bg: string;
  card: string;
  fg: string;
  mutedFg: string;
  border: string;
}

// Mirrors GymPalette.LIGHT_ALL (backend) — si una cambia, actualizar la otra. A diferencia
// de PALETTES (acento libre + fondo derivado del hue, siempre oscuro), estas son 4 combos
// completos curados a mano: fondo+tarjeta+texto+acento, verificados contra la fórmula real
// de contraste (contrastRatio de arriba, ≥4.5:1 en texto/fondo, texto/tarjeta y blanco
// sobre el botón de acento) porque la heurística de contraste aproximada (luminancia simple
// con umbral fijo) no alcanza para garantizar esto sobre cualquier tono.
export const LIGHT_PALETTES: LightPaletteEntry[] = [
  {
    key: 'amanecer',
    label: 'Amanecer',
    hex: '#C2410C',
    contrast: '#FFFFFF',
    bg: '#FFF8F1',
    card: '#FFFFFF',
    fg: '#241206',
    mutedFg: '#78716C',
    border: '#F2DCC8',
  },
  {
    key: 'oceano',
    label: 'Océano',
    hex: '#2563EB',
    contrast: '#FFFFFF',
    bg: '#F5F9FF',
    card: '#FFFFFF',
    fg: '#0F172A',
    mutedFg: '#475569',
    border: '#DCE7FB',
  },
  {
    key: 'menta',
    label: 'Menta',
    hex: '#047857',
    contrast: '#FFFFFF',
    bg: '#F3FBF7',
    card: '#FFFFFF',
    fg: '#062E22',
    mutedFg: '#4B6358',
    border: '#D6EEE2',
  },
  {
    key: 'frambuesa',
    label: 'Frambuesa',
    hex: '#DB2777',
    contrast: '#FFFFFF',
    bg: '#FFF5F8',
    card: '#FFFFFF',
    fg: '#3F0716',
    mutedFg: '#8A5A67',
    border: '#F7D7E3',
  },
];

// El rojo de peligro actual (--ion-color-danger, #ff5d73) fue calibrado contra el fondo
// oscuro fijo (~4.85:1) — no alcanza contraste sobre un fondo claro. Verificado: ≥4.5:1
// como texto blanco encima Y como texto propio sobre cualquiera de las 4 tarjetas claras.
export const LIGHT_DANGER = {
  base: '#DC2626',
  contrast: '#FFFFFF',
  shade: '#c22121',
  tint: '#e03c3c',
};

function findLightPalette(accentHex: string): LightPaletteEntry {
  return (
    LIGHT_PALETTES.find((p) => p.hex.toLowerCase() === accentHex.toLowerCase()) ?? LIGHT_PALETTES[0]
  );
}

/**
 * Derives a subtle panel background + card tone from a gym's accent color.
 * Modo oscuro (default, sin cambios): matching the same darkness/saturation as the app's
 * own brand ink (#1e2c30 is ~H200 S23 L15) so every gym feels like a variation of the same
 * product, not an unrelated color scheme — deriva la superficie del HUE del acento libre.
 * Modo claro: no deriva nada por fórmula — busca la paleta curada correspondiente al acento
 * (ver LIGHT_PALETTES) y devuelve su fondo/tarjeta ya fijos.
 * Shared by member/join/gym-admin so las 3 superficies derivan el mismo look del mismo acento.
 */
export function deriveSurfaceTint(accentHex: string, mode: ThemeMode = 'DARK'): { bg: string; card: string } {
  if (mode === 'LIGHT') {
    const palette = findLightPalette(accentHex);
    return { bg: palette.bg, card: palette.card };
  }
  const hue = hexToHue(accentHex);
  return { bg: hslToHex(hue, 22, 15), card: hslToHex(hue, 22, 19) };
}

const ION_STEP_NUMBERS = Array.from({ length: 19 }, (_, i) => 50 + i * 50); // 50, 100, ..., 950

/**
 * Ionic components internamente leen una "escalera" de 19 tonos
 * (--ion-text-color-step-50..950 / --ion-background-color-step-50..950) para sombrear texto/
 * fondos secundarios en overlays de shadow DOM (ion-select→ion-alert, ion-action-sheet,
 * ion-picker, ion-toast) — hoy están hardcodeados en styles.scss como una interpolación
 * lineal de 19 pasos entre --brand-text y --brand-ink (ver comentario ahí, encontrado por un
 * bug real: el picker de hora quedaba con texto casi invisible sin esto). Esta función
 * generaliza esa misma interpolación para poder construir el ramp de cualquier paleta clara
 * al vuelo, en vez de tener que hardcodear un segundo bloque de 38 líneas a mano.
 */
export function buildIonStepRamp(textHex: string, inkHex: string): Record<string, string> {
  const text = hexToRgb(textHex);
  const ink = hexToRgb(inkHex);
  const lerp = (a: number, b: number, t: number) => Math.round(a + (b - a) * t);
  const toHex = (n: number) => n.toString(16).padStart(2, '0');
  const mix = (from: { r: number; g: number; b: number }, to: { r: number; g: number; b: number }, t: number) =>
    `#${toHex(lerp(from.r, to.r, t))}${toHex(lerp(from.g, to.g, t))}${toHex(lerp(from.b, to.b, t))}`;

  const vars: Record<string, string> = {};
  ION_STEP_NUMBERS.forEach((step, index) => {
    const t = index / (ION_STEP_NUMBERS.length - 1);
    vars[`--ion-text-color-step-${step}`] = mix(text, ink, t);
    vars[`--ion-background-color-step-${step}`] = mix(ink, text, t);
  });
  return vars;
}

const THEME_OVERRIDE_PROPERTY_NAMES = [
  'color-scheme',
  '--brand-ink',
  '--brand-ink-surface',
  '--brand-ink-surface-2',
  '--brand-text',
  '--brand-text-dim',
  '--ion-color-danger',
  '--ion-color-danger-rgb',
  '--ion-color-danger-contrast',
  '--ion-color-danger-contrast-rgb',
  '--ion-color-danger-shade',
  '--ion-color-danger-tint',
  ...ION_STEP_NUMBERS.flatMap((step) => [`--ion-text-color-step-${step}`, `--ion-background-color-step-${step}`]),
];

/**
 * Modo claro necesita bastante más que el acento/superficie de un gym puntual: los tokens
 * base fijos de styles.scss (--brand-ink, --ion-color-danger, el step-ramp completo) asumen
 * "siempre oscuro" a nivel de toda la app, no por-gym. Mismo mecanismo que ya usaba
 * gym-admin.ts para sus 7 variables (escribir en document.documentElement porque los overlays
 * de Ionic con shadow DOM se portan fuera de ion-content/ion-modal) — generalizado acá para
 * reusarlo también desde member.ts y join.ts. Llamar SOLO cuando el gym activo está en modo
 * claro; en modo oscuro no tocar documentElement — el :root oscuro de styles.scss sigue
 * aplicando tal cual, cero riesgo para gyms que no cambian nada.
 */
export function applyLightThemeOverrides(accentHex: string): void {
  const palette = findLightPalette(accentHex);
  const root = document.documentElement.style;
  root.setProperty('color-scheme', 'light');
  root.setProperty('--brand-ink', palette.bg);
  root.setProperty('--brand-ink-surface', palette.card);
  root.setProperty('--brand-ink-surface-2', palette.border);
  root.setProperty('--brand-text', palette.fg);
  root.setProperty('--brand-text-dim', palette.mutedFg);

  const rgb = hexToRgb(LIGHT_DANGER.base);
  const contrastRgb = hexToRgb(LIGHT_DANGER.contrast);
  root.setProperty('--ion-color-danger', LIGHT_DANGER.base);
  root.setProperty('--ion-color-danger-rgb', `${rgb.r}, ${rgb.g}, ${rgb.b}`);
  root.setProperty('--ion-color-danger-contrast', LIGHT_DANGER.contrast);
  root.setProperty('--ion-color-danger-contrast-rgb', `${contrastRgb.r}, ${contrastRgb.g}, ${contrastRgb.b}`);
  root.setProperty('--ion-color-danger-shade', LIGHT_DANGER.shade);
  root.setProperty('--ion-color-danger-tint', LIGHT_DANGER.tint);

  Object.entries(buildIonStepRamp(palette.fg, palette.bg)).forEach(([name, value]) => root.setProperty(name, value));
}

/** Contraparte de applyLightThemeOverrides — llamar siempre que el gym activo esté (o pase
 *  a estar) en modo oscuro, y en ngOnDestroy, para no filtrar overrides claros a otras rutas. */
export function clearThemeOverrides(): void {
  const root = document.documentElement.style;
  THEME_OVERRIDE_PROPERTY_NAMES.forEach((name) => root.removeProperty(name));
}

/** Atajo para el caso común: aplicar o limpiar según el modo actual del gym, en un solo
 *  llamado — mismo criterio en los 4 lugares que lo usan (member/join/gym-admin/gym-form). */
export function syncThemeOverrides(mode: ThemeMode | null | undefined, accentHex: string | null | undefined): void {
  if (mode === 'LIGHT' && accentHex) {
    applyLightThemeOverrides(accentHex);
  } else {
    clearThemeOverrides();
  }
}
