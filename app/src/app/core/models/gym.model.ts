export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface Gym {
  id: number;
  name: string;
  slug: string;
  active: boolean;
  maxUsers: number;
  googleLoginEnabled: boolean;
  themeColor: string | null;
  logoSvg: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Branding-only view for the public join page (mygym.cl/j/{slug}) — no auth required to fetch this. */
export interface PublicGym {
  name: string;
  slug: string;
  themeColor: string;
  themeContrast: string;
  logoSvg: string | null;
  googleLoginEnabled: boolean;
}

export interface GymBlock {
  id: number;
  gymId: number;
  label: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  capacity: number;
  active: boolean;
}

const DAY_ORDER: Record<DayOfWeek, number> = {
  MONDAY: 0,
  TUESDAY: 1,
  WEDNESDAY: 2,
  THURSDAY: 3,
  FRIDAY: 4,
  SATURDAY: 5,
  SUNDAY: 6,
};

/** Weekly-schedule order: day of week first (Mon→Sun), then start time within each day. */
export function sortBlocksBySchedule(blocks: GymBlock[]): GymBlock[] {
  return [...blocks].sort((a, b) => {
    const dayDiff = DAY_ORDER[a.dayOfWeek] - DAY_ORDER[b.dayOfWeek];
    return dayDiff !== 0 ? dayDiff : a.startTime.localeCompare(b.startTime);
  });
}

export interface Admin {
  id: number;
  name: string;
  email: string;
  active: boolean;
}

export interface CreateGymRequest {
  name: string;
  slug: string;
  maxUsers: number;
  ownerName: string;
  ownerEmail: string;
  themeColor?: string | null;
  logoSvg?: string | null;
}

export interface BrandingSuggestion {
  themeColor: string;
  logoSvg: string;
}

export interface CreateAdminRequest {
  name: string;
  email: string;
}

export interface GymConfigUpdateRequest {
  active: boolean;
  maxUsers: number;
  googleLoginEnabled: boolean;
  logoSvg: string | null;
}

export interface CreateGymBlockRequest {
  label: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  capacity: number;
}

export interface UpdateGymBlockRequest extends CreateGymBlockRequest {
  active: boolean;
}

export interface GymPlan {
  id: number;
  gymId: number;
  name: string;
  description: string | null;
  /** Etiqueta libre (ej. "personal", "libre", "estudiante") — solo para mostrar, no una categoría cerrada. */
  category: string | null;
  priceClp: number;
  /** Null = plan libre (reserva ilimitada en los cupos disponibles). */
  monthlyClasses: number | null;
  active: boolean;
}

export interface CreateGymPlanRequest {
  name: string;
  description: string | null;
  category: string | null;
  priceClp: number;
  monthlyClasses: number | null;
}

export interface UpdateGymPlanRequest extends CreateGymPlanRequest {
  active: boolean;
}

/** Vista del socio de un plan activo de su gym — sin gymId ni active (GET /api/me/plans). */
export interface MemberPlan {
  id: number;
  name: string;
  description: string | null;
  category: string | null;
  priceClp: number;
  monthlyClasses: number | null;
}
