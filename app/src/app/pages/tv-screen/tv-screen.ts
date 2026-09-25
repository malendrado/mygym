import { NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { IonContent } from '@ionic/angular';
import { TvAttendeeSummary, TvBlockOccurrence, TvSchedule } from '../../core/models/tv-screen.model';
import { TvScreenService } from '../../core/services/tv-screen.service';
import { FitTier, TvFitDirective } from './tv-fit.directive';
import {
  ThemeMode,
  clearThemeOverrides,
  deriveSurfaceTint,
  ensureMinContrastColor,
  syncThemeOverrides,
} from '../../core/utils/gym-theme';

const TV_TOKEN_KEY = 'mygym.tv.screenToken';
const SCHEDULE_POLL_MS = 25_000;
const PAIRING_POLL_MS = 3_000;
// Longevidad del kiosco: una pestaña de TV queda abierta meses — un reload periódico evita
// leaks de memoria de una SPA que nunca se cierra, sin cortar el show por más de un segundo.
const KIOSK_RELOAD_MS = 6 * 60 * 60 * 1000;
// Pasado este rato desde que terminó, "clase anterior" deja de ser información útil y se oculta
// en vez de quedar mostrando la clase de la mañana toda la tarde.
const PREVIOUS_STALE_MINUTES = 90;
// Si el último poll exitoso quedó "congelado" (el backend cayó, la TV perdió wifi un rato), el
// reloj local sigue corriendo y en algún momento pasa el endTime de la clase que el servidor
// dijo que estaba "en curso". Sin este límite, esa clase queda pegada en "Ahora" para siempre
// con un countdown que ya no tiene sentido ("Termina ya" cuando en realidad ya terminó hace
// rato) — bug real visto en pantalla. Un margen de 60s cubre el drift normal entre polls
// (25s) sin ocultar de más.
const STALE_CURRENT_GRACE_SECONDS = 60;
// Si pasa mucho más que un poll normal sin éxito, mostrar un aviso sutil de que la pantalla no
// se pudo actualizar — mejor decirlo que dejar todo congelado en silencio indefinidamente.
const RECONNECTING_THRESHOLD_MS = SCHEDULE_POLL_MS * 3;
// Fotos mínimas por vuelta de la tira (≈ 210px c/u): suficiente para cubrir la columna izquierda
// de una TV de 1920px sin hueco antes de que empiece la copia del loop.
const PHOTO_STRIP_MIN_ITEMS = 8;
// Colores fijos para diferenciar planes a simple vista en el roster — a propósito distinto del
// acento único de marca del gym (que solo pinta UNA cosa): acá el objetivo es que dos socios
// con planes distintos se distingan de lejos, así que el color mismo es la señal.
const PLAN_PALETTE = ['#f97316', '#3b82f6', '#22c55e', '#ec4899', '#eab308', '#a855f7'];

// Una TV no tiene scroll — nada puede depender de más espacio del que existe. Lo que no cabe se
// PAGINA (carrusel) en vez de achicarse o esconderse tras un "+N": clases en curso de a 2,
// próximas de a 1, asistentes y agenda por páginas según el espacio medido. Cada página dura
// ~10 s: en una TV nadie puede apretar "siguiente", tiene que alcanzar a buscarse en la lista.
const HERO_PAGE_SIZE = 2;
const CAROUSEL_PAGE_MS = 10_000;

// Tamaños de chip de asistente, de mayor a menor (rem). TvFitDirective mide el espacio real y
// elige el más grande en el que entran todos; si ni el chico alcanza, "+N". Los 3 primeros
// muestran nombre y apellido completos + píldora de plan; "s" muestra nombre + inicial del
// apellido y el plan solo como color del anillo (decisión explícita del usuario para espacios
// chicos).
const ROSTER_TIERS: FitTier[] = [
  { name: 'xl', w: 19, h: 5.2, gap: 1 },
  { name: 'l', w: 14.5, h: 4.6, gap: 0.85 },
  { name: 'm', w: 12.5, h: 3.2, gap: 0.6 },
  { name: 's', w: 9.5, h: 2.3, gap: 0.5 },
];

// Mínimo garantizado de un roster lateral (una fila de chips chicos, ver .tv-roster-fit en el
// SCSS): ese alto ya lo tiene por flex-basis, el reparto solo cubre lo que falta por encima.
const SIDEBAR_ROSTER_MIN_REM = 2.3;
// Caras visibles por fila de agenda en tamaño row-xl (el total ya lo dice el cupo "6/10").
const AGENDA_MAX_FACES = 7;

// Filas de la agenda "más tarde": ancho completo, alto fijo.
// "row-xl" (solo si sobra alto) agrega una 2ª línea con categoría/instructor y las caras de
// quienes ya reservaron — enriquece con datos que ya tenemos en vez de dejar alto vacío.
const AGENDA_TIERS: FitTier[] = [
  { name: 'row-xl', w: 0, h: 5.4, gap: 0.6 },
  { name: 'row-l', w: 0, h: 3.4, gap: 0.5 },
  { name: 'row', w: 0, h: 2.3, gap: 0.35 },
];

type Stage = 'pairing' | 'loading' | 'display' | 'error';

function chunk<T>(list: T[], size: number): T[][] {
  const pages: T[][] = [];
  for (let i = 0; i < list.length; i += size) pages.push(list.slice(i, i + size));
  return pages;
}

function solidFillTextColor(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 0.6 ? '#0c1210' : '#ffffff';
}

/**
 * Pantalla pública sin login (ver comentario en web.routes.ts): se autoemparaja con un código
 * de 6 caracteres que un GYM_ADMIN escribe UNA vez desde su panel — la TV nunca tipea nada.
 * El token de pantalla queda en localStorage y no vence salvo 90 días sin poll (ver
 * TvScreenService backend) o que el admin la desvincule.
 *
 * Layout fijo (no rotativo): tarjeta grande con la(s) clase(s) EN CURSO + dos tarjetas chicas
 * al costado (clase anterior / próxima clase) — decisión explícita del usuario para que "ahora"
 * siempre esté en el mismo lugar en vez de andar rotando.
 */
@Component({
  selector: 'app-tv-screen',
  standalone: true,
  imports: [IonContent, NgTemplateOutlet, TvFitDirective],
  templateUrl: './tv-screen.html',
  styleUrl: './tv-screen.scss',
})
export class TvScreenPage implements OnDestroy {
  private readonly tvScreenService = inject(TvScreenService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly stage = signal<Stage>('pairing');
  protected readonly pairingCode = signal<string | null>(null);
  protected readonly schedule = signal<TvSchedule | null>(null);
  protected readonly now = signal<Date>(new Date());

  protected readonly isRasterLogo = computed(() => (this.schedule()?.logoSvg ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.schedule()?.logoSvg;
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });

  // Duplicada para que el scroll infinito (CSS puro, ver tv-screen.scss) cierre el loop sin
  // salto visible — mismo patrón ya probado en member.ts/member.scss.
  // Con pocas fotos (ej. 2), una vuelta no alcanza a cubrir el ancho de la columna y quedaba un
  // hueco a la derecha — se repite la lista hasta tener al menos PHOTO_STRIP_MIN_ITEMS por vuelta.
  protected readonly photoStrip = computed(() => {
    const list = this.schedule()?.photos ?? [];
    if (!list.length) return [];
    let lap = [...list];
    while (lap.length < PHOTO_STRIP_MIN_ITEMS) lap = [...lap, ...list];
    return [...lap, ...lap];
  });

  protected readonly themeMode = computed<ThemeMode>(() =>
    this.schedule()?.themeMode === 'LIGHT' ? 'LIGHT' : 'DARK',
  );

  protected readonly surfaceBg = computed(() => {
    const color = this.schedule()?.themeColor;
    return color ? deriveSurfaceTint(color, this.themeMode()).bg : null;
  });

  protected readonly surfaceCard = computed(() => {
    const color = this.schedule()?.themeColor;
    return color ? deriveSurfaceTint(color, this.themeMode()).card : null;
  });

  protected readonly accentText = computed(() => {
    const color = this.schedule()?.themeColor;
    if (!color) return null;
    return ensureMinContrastColor(color, this.surfaceBg() ?? '#1e2c30');
  });

  protected readonly accentSolid = computed(() => this.schedule()?.themeColor ?? null);
  protected readonly accentSolidText = computed(() => {
    const color = this.accentSolid();
    return color ? solidFillTextColor(color) : null;
  });

  // Filtra lo que el servidor dijo que era "actual" contra el reloj local: si el poll que trajo
  // este dato quedó viejo y la clase ya pasó su endTime hace rato, no se sigue mostrando como en
  // curso — ver STALE_CURRENT_GRACE_SECONDS arriba.
  private readonly allCurrentBlocks = computed<TvBlockOccurrence[]>(() => {
    const all = this.schedule()?.current ?? [];
    const parts = this.santiagoParts(this.now());
    return all.filter((occ) => {
      if (occ.classDate !== parts.dateIso) return true;
      const [eh, em] = occ.endTime.split(':').map(Number);
      const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
      const endSec = eh * 3600 + em * 60;
      return nowSec - endSec < STALE_CURRENT_GRACE_SECONDS;
    });
  });
  private readonly allNextBlocks = computed<TvBlockOccurrence[]>(() => this.schedule()?.next ?? []);

  private readonly lastSuccessfulPollAt = signal<number>(Date.now());
  // Aviso sutil de que el sondeo lleva rato sin poder actualizar — mejor decirlo que dejar la
  // pantalla congelada en silencio (ver STALE_CURRENT_GRACE_SECONDS / RECONNECTING_THRESHOLD_MS).
  protected readonly isReconnecting = computed(() => {
    this.now(); // dispara la recomputación cada segundo — el cálculo real usa Date.now() (reloj real, sin el ajuste de zona horaria de now()).
    return Date.now() - this.lastSuccessfulPollAt() > RECONNECTING_THRESHOLD_MS;
  });

  // Reloj ÚNICO de páginas para todos los carruseles de la pantalla (clases en curso, próximas,
  // asistentes, agenda): todos cambian en el mismo instante. Si cada zona girara a su propio
  // ritmo la pantalla se vería nerviosa (skill ui-ux-pro-max / buenas prácticas de signage).
  // Derivado del reloj → no necesita timer propio.
  protected readonly pageTick = computed(() => Math.floor(this.now().getTime() / CAROUSEL_PAGE_MS));

  // Sin clase en curso: "próxima clase" pasa a ocupar el lugar protagónico — mismo trato
  // completo que "ahora", es la información más útil en ese momento porque todavía se puede
  // reservar (feedback real del usuario: dejar esa área con un aviso era espacio muerto).
  protected readonly isIdle = computed(() => this.allCurrentBlocks().length === 0);

  // Tarjeta grande: SIEMPRE bloques de a lo más HERO_PAGE_SIZE clases (decisión del usuario —
  // con 3+ simultáneas, en vez de achicarlas todas, se pagina de a 2 y se turnan).
  private readonly heroPages = computed<TvBlockOccurrence[][]>(() =>
    chunk(this.isIdle() ? this.allNextBlocks() : this.allCurrentBlocks(), HERO_PAGE_SIZE),
  );
  protected readonly heroPageCount = computed(() => this.heroPages().length);
  protected readonly heroPageIndex = computed(() => this.pageTick() % Math.max(this.heroPageCount(), 1));
  protected readonly heroBlocks = computed<TvBlockOccurrence[]>(() => this.heroPages()[this.heroPageIndex()] ?? []);
  protected readonly heroLabel = computed(() => {
    if (!this.isIdle()) return 'Ahora';
    return this.allNextBlocks().length > 1 ? 'Próximas clases' : 'Próxima clase';
  });
  // Si la tarjeta grande vino de "ahora", la clase ya empezó (countdown, progreso, sin avisos
  // de cupo porque las inscripciones ya cerraron). Si vino de "próxima" (promovida por estar
  // idle), todavía no empieza — al revés en los tres casos.
  protected readonly isHeroLive = computed(() => !this.isIdle());

  // "Próxima clase" en la columna lateral (solo con una clase en curso — si no, ya está en
  // grande). De a UNA por vez con su roster completo; si hay varias simultáneas, se turnan
  // (antes se apilaban 2 y no quedaba alto para ver a nadie).
  private readonly sidebarNextAll = computed<TvBlockOccurrence[]>(() => (this.isIdle() ? [] : this.allNextBlocks()));
  protected readonly sidebarNextCount = computed(() => this.sidebarNextAll().length);
  protected readonly sidebarNextIndex = computed(() => this.pageTick() % Math.max(this.sidebarNextCount(), 1));
  protected readonly sidebarNextBlocks = computed<TvBlockOccurrence[]>(() => {
    const occ = this.sidebarNextAll()[this.sidebarNextIndex()];
    return occ ? [occ] : [];
  });

  // Agenda "más tarde": el resto de las clases de ese día (las "próximas" simultáneas ya se
  // muestran todas vía carrusel arriba, no se repiten acá).
  protected readonly laterItems = computed<TvBlockOccurrence[]>(() => this.schedule()?.later ?? []);

  protected readonly laterLabel = computed(() => {
    const nextDate = this.schedule()?.nextDate;
    const today = this.santiagoParts(this.now()).dateIso;
    if (!nextDate || nextDate === today) return this.isIdle() ? 'Después' : 'Más tarde hoy';
    const weekday = new Intl.DateTimeFormat('es-CL', { timeZone: 'America/Santiago', weekday: 'long' }).format(
      new Date(`${nextDate}T12:00:00`),
    );
    return `Después · ${weekday}`;
  });

  // Sin ningún contenido lateral (ni anterior, ni próxima al costado, ni agenda), la tarjeta
  // grande toma todo el ancho en vez de dejar una columna vacía.
  protected readonly showSidebar = computed(
    () => this.previousBlock() !== null || this.sidebarNextBlocks().length > 0 || this.laterItems().length > 0,
  );

  // El id de plan es global (no correlativo por gym) — un simple "id % paleta" colisiona apenas
  // dos planes de un mismo gym caen en el mismo resto (visto en la práctica: id 3 y 27 con
  // paleta de 6 daban el mismo color). Se asigna color por POSICIÓN entre los planes que
  // realmente aparecen en pantalla ahora mismo (orden estable por id) — así nunca colisionan
  // mientras el gym tenga ≤6 planes visibles a la vez, que es siempre el caso real.
  protected readonly planColorMap = computed<Map<number, string>>(() => {
    const sched = this.schedule();
    const ids = new Set<number>();
    if (sched) {
      const groups = [...sched.current, ...sched.next, ...(sched.later ?? []), ...(sched.previous ? [sched.previous] : [])];
      groups.forEach((occ) => occ.attendees.forEach((a) => a.planId != null && ids.add(a.planId)));
    }
    const sortedIds = [...ids].sort((a, b) => a - b);
    return new Map(sortedIds.map((id, index) => [id, PLAN_PALETTE[index % PLAN_PALETTE.length]]));
  });

  // Se oculta sola pasados PREVIOUS_STALE_MINUTES desde que terminó — ver constante arriba.
  protected readonly previousBlock = computed<TvBlockOccurrence | null>(() => {
    const prev = this.schedule()?.previous ?? null;
    if (!prev) return null;
    const parts = this.santiagoParts(this.now());
    if (prev.classDate !== parts.dateIso) return null;
    const [eh, em] = prev.endTime.split(':').map(Number);
    const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
    const endSec = eh * 3600 + em * 60;
    const elapsedMin = (nowSec - endSec) / 60;
    return elapsedMin > PREVIOUS_STALE_MINUTES ? null : prev;
  });

  private clockOffsetMs = 0;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private kioskReloadTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.clockTimer = setInterval(() => this.now.set(new Date(Date.now() + this.clockOffsetMs)), 1000);
    this.kioskReloadTimer = setTimeout(() => location.reload(), KIOSK_RELOAD_MS);

    const storedToken = localStorage.getItem(TV_TOKEN_KEY);
    if (storedToken) {
      this.stage.set('loading');
      this.startSchedulePolling(storedToken);
    } else {
      this.startPairing();
    }
  }

  ngOnDestroy(): void {
    if (this.clockTimer) clearInterval(this.clockTimer);
    if (this.pollTimer) clearTimeout(this.pollTimer);
    if (this.kioskReloadTimer) clearTimeout(this.kioskReloadTimer);
    clearThemeOverrides();
  }

  private startPairing(): void {
    this.stage.set('pairing');
    this.pairingCode.set(null);
    this.tvScreenService.createPairing().subscribe({
      next: (res) => {
        this.pairingCode.set(res.code);
        this.pollPairingStatus(res.code);
      },
      error: () => {
        this.stage.set('error');
        this.pollTimer = setTimeout(() => this.startPairing(), 15_000);
      },
    });
  }

  private pollPairingStatus(code: string): void {
    this.tvScreenService.pairingStatus(code).subscribe({
      next: (status) => {
        if (status.claimed && status.screenToken) {
          localStorage.setItem(TV_TOKEN_KEY, status.screenToken);
          this.stage.set('loading');
          this.startSchedulePolling(status.screenToken);
          return;
        }
        this.pollTimer = setTimeout(() => this.pollPairingStatus(code), PAIRING_POLL_MS);
      },
      error: () => {
        // El código venció (10 min) sin que nadie lo vinculara — pedimos uno nuevo en silencio,
        // la TV nunca necesita que alguien intervenga a mano.
        this.startPairing();
      },
    });
  }

  private startSchedulePolling(token: string): void {
    const poll = () => {
      this.tvScreenService.schedule(token).subscribe({
        next: (data) => {
          this.clockOffsetMs = new Date(data.serverTime).getTime() - Date.now();
          this.lastSuccessfulPollAt.set(Date.now());
          this.schedule.set(data);
          this.stage.set('display');
          syncThemeOverrides(data.themeMode === 'LIGHT' ? 'LIGHT' : 'DARK', data.themeColor);
          this.pollTimer = setTimeout(poll, SCHEDULE_POLL_MS);
        },
        error: (err) => {
          if (err?.status === 404) {
            // La pantalla se desvinculó o pasó 90 días sin conectarse — hay que emparejar de nuevo.
            localStorage.removeItem(TV_TOKEN_KEY);
            this.schedule.set(null);
            clearThemeOverrides();
            this.startPairing();
            return;
          }
          // Error transitorio de red: reintenta sin romper lo que ya está en pantalla.
          this.pollTimer = setTimeout(poll, SCHEDULE_POLL_MS);
        },
      });
    };
    poll();
  }

  protected clockLabel(): string {
    return new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).format(this.now());
  }

  protected dateLabel(): string {
    const label = new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(this.now());
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected startLabel(occurrence: TvBlockOccurrence): string {
    return occurrence.startTime.slice(0, 5);
  }

  // Rango completo ("17:05–17:30") en vez de solo la hora de inicio — responde de un vistazo
  // "¿me alcanza antes de mi próximo compromiso?", sobre todo en las tarjetas chicas que no
  // tienen countdown para compensar.
  protected timeRangeLabel(occurrence: TvBlockOccurrence): string {
    return `${occurrence.startTime.slice(0, 5)}–${occurrence.endTime.slice(0, 5)}`;
  }

  protected dayLabel(occurrence: TvBlockOccurrence): string {
    if (occurrence.classDate === this.santiagoParts(this.now()).dateIso) return 'Hoy';
    const label = new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      weekday: 'long',
    }).format(new Date(`${occurrence.classDate}T12:00:00`));
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected remainingLabel(occurrence: TvBlockOccurrence): string {
    const parts = this.santiagoParts(this.now());
    if (occurrence.classDate !== parts.dateIso) return '';
    const [eh, em] = occurrence.endTime.split(':').map(Number);
    const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
    const endSec = eh * 3600 + em * 60;
    const remaining = endSec - nowSec;
    if (remaining <= 0) return 'Termina ya';
    const mins = Math.floor(remaining / 60);
    if (mins < 1) return 'Último minuto';
    return `Quedan ${mins} min`;
  }

  // Fracción [0,100] transcurrida de la clase — para la barra de progreso de "ahora".
  protected progressPercent(occurrence: TvBlockOccurrence): number {
    const parts = this.santiagoParts(this.now());
    if (occurrence.classDate !== parts.dateIso) return 0;
    const [sh, sm] = occurrence.startTime.split(':').map(Number);
    const [eh, em] = occurrence.endTime.split(':').map(Number);
    const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
    const totalSec = eh * 3600 + em * 60 - (sh * 3600 + sm * 60);
    if (totalSec <= 0) return 0;
    const elapsedSec = nowSec - (sh * 3600 + sm * 60);
    return Math.min(100, Math.max(0, Math.round((elapsedSec / totalSec) * 100)));
  }

  // Contraparte de remainingLabel para una clase que todavía no empieza (hero promovido desde
  // "próxima" cuando no hay nada en curso) — "Empieza en X min" en vez de "Quedan X min".
  protected startsInLabel(occurrence: TvBlockOccurrence): string {
    const parts = this.santiagoParts(this.now());
    if (occurrence.classDate !== parts.dateIso) {
      return `${this.dayLabel(occurrence)} ${this.startLabel(occurrence)}`;
    }
    const [sh, sm] = occurrence.startTime.split(':').map(Number);
    const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
    const remainingSec = sh * 3600 + sm * 60 - nowSec;
    if (remainingSec <= 0) return 'Empieza ya';
    const mins = Math.round(remainingSec / 60);
    if (mins < 1) return 'Empieza ya';
    return `Empieza en ${mins} min`;
  }

  protected isFull(occurrence: TvBlockOccurrence): boolean {
    return occurrence.capacity != null && occurrence.taken >= occurrence.capacity;
  }

  // Pocos cupos pero todavía no lleno — urgencia real sin llegar al badge de "cupo lleno".
  protected isLowCapacity(occurrence: TvBlockOccurrence): boolean {
    if (occurrence.capacity == null) return false;
    const remaining = occurrence.capacity - occurrence.taken;
    return remaining > 0 && remaining <= 3;
  }

  protected capacityLabel(occurrence: TvBlockOccurrence): string {
    if (occurrence.capacity == null) return `${occurrence.taken} anotados`;
    return `${occurrence.taken}/${occurrence.capacity}`;
  }

  protected readonly rosterTiers = ROSTER_TIERS;
  protected readonly agendaTiers = AGENDA_TIERS;

  // Nombre según el tamaño de chip que eligió la medición: apellido completo cuando hay lugar,
  // inicial ("Camila S.") en el tamaño chico.
  protected chipName(attendee: TvAttendeeSummary, tier: string): string {
    if (!attendee.lastName) return attendee.firstName;
    return tier === 's' ? `${attendee.firstName} ${attendee.lastName.charAt(0)}.` : `${attendee.firstName} ${attendee.lastName}`;
  }

  // Reparto del alto de la columna lateral: cada tarjeta toma primero el alto natural de su
  // encabezado + el mínimo garantizado de su zona medida (flex-basis auto — las zonas medidas no
  // aportan más alto propio, ver contain: size en el SCSS) y SOLO el espacio sobrante se
  // reparte, en proporción a cuánto alto le falta a cada zona para mostrar todo (en rem).
  // Alturas de referencia = tamaño de chip chico (s) con 3 columnas y filas de agenda "row",
  // que es lo mínimo con lo que todos entran en la columna lateral.
  private rosterNeedRem(attendees: number): number {
    if (attendees === 0) return 0;
    const rows = Math.ceil(attendees / 3);
    return rows * 2.3 + (rows - 1) * 0.5;
  }

  private agendaNeedRem(items: number): number {
    return items === 0 ? 0 : items * 2.3 + (items - 1) * 0.35;
  }

  protected occurrenceWeight(occurrence: TvBlockOccurrence): number {
    return Math.max(this.rosterNeedRem(occurrence.attendees.length) - SIDEBAR_ROSTER_MIN_REM, 0.1);
  }

  protected readonly sidebarNextWeight = computed(() =>
    this.sidebarNextBlocks().reduce((sum, occ) => sum + this.occurrenceWeight(occ), 0),
  );

  // Mínimo garantizado de la agenda: siempre al menos 2 filas visibles si hay 2 o más clases.
  protected readonly laterMinRem = computed(() => (this.laterItems().length > 1 ? 4.95 : 2.3));

  // Tope de la agenda: el alto de todas sus filas en el tamaño más grande (row-xl). Pasado eso,
  // más alto solo serían filas vacías — el sobrante lo toman las tarjetas con asistentes.
  protected readonly laterMaxRem = computed(() => {
    const n = this.laterItems().length;
    return n === 0 ? 0 : n * AGENDA_TIERS[0].h + (n - 1) * AGENDA_TIERS[0].gap;
  });

  protected readonly laterWeight = computed(() =>
    Math.max(this.agendaNeedRem(this.laterItems().length) - this.laterMinRem(), 0.1),
  );

  protected agendaFaces(occ: TvBlockOccurrence): TvAttendeeSummary[] {
    return occ.attendees.slice(0, AGENDA_MAX_FACES);
  }

  protected pagerDots(count: number): number[] {
    return Array.from({ length: count }, (_, i) => i);
  }

  protected planColor(planId: number | null): string {
    if (planId == null) return PLAN_PALETTE[0];
    return this.planColorMap().get(planId) ?? PLAN_PALETTE[0];
  }

  protected planTextColor(planId: number | null): string {
    return solidFillTextColor(this.planColor(planId));
  }

  private santiagoParts(date: Date): { dateIso: string; hh: number; mm: number; ss: number } {
    const dateIso = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(date);
    const timeStr = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'America/Santiago',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).format(date);
    const [hh, mm, ss] = timeStr.split(':').map(Number);
    return { dateIso, hh, mm, ss };
  }
}
