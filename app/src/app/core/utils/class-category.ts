import { addIcons } from 'ionicons';
import {
  accessibilityOutline,
  barbellOutline,
  bicycleOutline,
  bodyOutline,
  fitnessOutline,
  flashOutline,
  handLeftOutline,
  musicalNotesOutline,
  walkOutline,
  waterOutline,
} from 'ionicons/icons';

/**
 * Categoría de clase — texto libre en la DB (Gym.category), no un enum
 * cerrado: cada gym puede escribir lo que quiera. Esta lista es solo de
 * SUGERENCIAS (chips) para no forzar a tipear siempre lo mismo, y el ícono se
 * resuelve por palabra clave — así una categoría no sugerida ("Crossfit Kids")
 * igual encuentra un ícono razonable en vez de caer siempre al genérico.
 */
export interface ClassCategoryOption {
  label: string;
  icon: string;
}

export const CLASS_CATEGORY_OPTIONS: ClassCategoryOption[] = [
  { label: 'Spinning', icon: 'bicycle-outline' },
  { label: 'Yoga', icon: 'body-outline' },
  { label: 'Crossfit', icon: 'barbell-outline' },
  { label: 'Funcional', icon: 'flash-outline' },
  { label: 'Boxeo', icon: 'hand-left-outline' },
  { label: 'Pilates', icon: 'accessibility-outline' },
  { label: 'Musculación', icon: 'barbell-outline' },
  { label: 'Natación', icon: 'water-outline' },
  { label: 'Baile / Zumba', icon: 'musical-notes-outline' },
];

const DEFAULT_ICON = 'fitness-outline';

const KEYWORD_ICONS: { keywords: string[]; icon: string }[] = [
  { keywords: ['spinning', 'bici', 'ciclismo', 'indoor'], icon: 'bicycle-outline' },
  { keywords: ['yoga', 'pilates', 'stretch', 'elongación'], icon: 'body-outline' },
  { keywords: ['crossfit', 'musculación', 'pesas', 'fuerza', 'gym'], icon: 'barbell-outline' },
  { keywords: ['funcional', 'hiit', 'circuito'], icon: 'flash-outline' },
  { keywords: ['box', 'boxeo', 'kick'], icon: 'hand-left-outline' },
  { keywords: ['natación', 'piscina', 'agua'], icon: 'water-outline' },
  { keywords: ['baile', 'zumba', 'ritmos'], icon: 'musical-notes-outline' },
  { keywords: ['running', 'trote', 'atletismo'], icon: 'walk-outline' },
];

let registered = false;

/** Llamar una vez por componente que use estos íconos (idempotente). */
export function registerClassCategoryIcons(): void {
  if (registered) {
    return;
  }
  registered = true;
  addIcons({
    'fitness-outline': fitnessOutline,
    'bicycle-outline': bicycleOutline,
    'body-outline': bodyOutline,
    'barbell-outline': barbellOutline,
    'flash-outline': flashOutline,
    'hand-left-outline': handLeftOutline,
    'accessibility-outline': accessibilityOutline,
    'water-outline': waterOutline,
    'musical-notes-outline': musicalNotesOutline,
    'walk-outline': walkOutline,
  });
}

export function resolveClassCategoryIcon(category: string | null): string {
  if (!category) {
    return DEFAULT_ICON;
  }
  const normalized = category.toLowerCase();
  const match = KEYWORD_ICONS.find((entry) => entry.keywords.some((keyword) => normalized.includes(keyword)));
  return match?.icon ?? DEFAULT_ICON;
}
