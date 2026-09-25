/** Espejo de TvScreenResponse (backend) — grilla "Pantallas" en gym-admin. */
export interface TvScreen {
  id: number;
  name: string;
  createdAt: string;
  /** Null = nunca hizo un poll real todavía (recién vinculada). */
  lastPolledAt: string | null;
}

export interface TvScreenClaimRequest {
  code: string;
  name: string;
}

/** Espejo de TvPairingCreateResponse. */
export interface TvPairingCreated {
  code: string;
  expiresAt: string;
}

/** Espejo de TvPairingStatusResponse — screenToken es null hasta que claimed sea true. */
export interface TvPairingStatus {
  claimed: boolean;
  screenToken: string | null;
}

/** Espejo de TvAttendeeResponse — nunca email, pero SÍ apellido (decisión explícita del usuario
 *  para esta pantalla, a diferencia de "Ver quién reservó"). lastName null si no está
 *  registrado. planName/planId son null si el socio no tiene un plan activo asignado. planId
 *  viaja aparte del texto para poder asignarle un color estable por id (ver planColor() en
 *  tv-screen.ts). */
export interface TvAttendeeSummary {
  firstName: string;
  lastName: string | null;
  photoUrl: string | null;
  planName: string | null;
  planId: number | null;
}

/** Espejo de TvBlockOccurrenceResponse. */
export interface TvBlockOccurrence {
  blockId: number;
  label: string;
  category: string | null;
  instructorName: string | null;
  /** Data URI, mismo campo que ya carga gym-admin. */
  instructorPhoto: string | null;
  /** "HH:mm:ss" (LocalTime de Java serializado). */
  startTime: string;
  endTime: string;
  /** "YYYY-MM-DD". */
  classDate: string;
  capacity: number | null;
  taken: number;
  attendees: TvAttendeeSummary[];
}

/** Espejo de GymPhotoResponse — misma galería que ya ve el socio en /member. */
export interface TvPhoto {
  id: number;
  data: string;
  caption: string | null;
}

/** Espejo de TvScheduleResponse — el payload que consume la pantalla en vivo. */
export interface TvSchedule {
  gymName: string;
  /** Igual que Gym.logoSvg en el resto de la app: data URI raster o markup SVG crudo. */
  logoSvg: string | null;
  themeColor: string | null;
  themeMode: string | null;
  tagline: string | null;
  photos: TvPhoto[];
  /** ISO instant — hora real del servidor, referencia para no depender 100% del reloj de la TV. */
  serverTime: string;
  current: TvBlockOccurrence[];
  next: TvBlockOccurrence[];
  /** Null si el gym no tiene ningún bloque activo. */
  nextDate: string | null;
  /** La clase de HOY que terminó más recientemente, null si todavía no terminó ninguna. */
  previous: TvBlockOccurrence | null;
  /** Resto de las clases del mismo día que nextDate, después del horario de "next" — agenda
   *  "más tarde". */
  later: TvBlockOccurrence[];
}
