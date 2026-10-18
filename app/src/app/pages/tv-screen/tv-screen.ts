import { NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { IonContent } from '@ionic/angular';
import { toCanvas as qrToCanvas } from 'qrcode';
import { TvAttendeeSummary, TvBlockOccurrence, TvPhoto, TvSchedule } from '../../core/models/tv-screen.model';
import { TvScreenService } from '../../core/services/tv-screen.service';
import { FitTier, TvFitDirective } from './tv-fit.directive';
import {
  ThemeMode,
  clearThemeOverrides,
  deriveSurfaceTint,
  ensureMinContrastColor,
  syncThemeOverrides,
} from '../../core/utils/gym-theme';
import { toLogoImgSrc } from '../../core/utils/logo-src';
import { VersionTag } from '../../core/components/version-tag/version-tag';

// Área del logo central como % del ancho del QR — bien por debajo del margen que da el nivel de
// corrección de errores 'H' (tolera perder hasta un 30% del área; un cuadrado de este lado ocupa
// ~7% del área total), así el celular sigue leyendo el código sin problema.
const QR_LOGO_SIZE_RATIO = 0.26;

const TV_TOKEN_KEY = 'mygym.tv.screenToken';
const SCHEDULE_POLL_MS = 25_000;
// La galería se baja al conectarse y se renueva cada 10 minutos (no viaja en cada poll del schedule).
const PHOTOS_REFRESH_MS = 600_000;
const PAIRING_POLL_MS = 3_000;
// Si falla pedir el código de vinculación, se reintenta solo a los 15 s (y la pantalla lo dice).
const PAIRING_RETRY_MS = 15_000;
// Longevidad del kiosco: una pestaña de TV queda abierta meses — un reload periódico evita
// leaks de memoria de una SPA que nunca se cierra, sin cortar el show por más de un segundo.
const TV_ROOT_FONT_SIZE = 'max(11px, min(calc(100vw / 96), calc(100vh / 54)))';
// El aviso "Pantalla completa" se retira solo a los 5 s para no tapar la agenda (vuelve si se sale de pantalla completa).
const FULLSCREEN_HINT_MS = 5_000;
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
// Si al arrancar (pantalla ya vinculada) pasan estos ms sin una primera respuesta buena del sondeo,
// se reemplaza el spinner por "Sin conexión con mygym" — un círculo girando para siempre no dice nada.
const OFFLINE_AFTER_MS = 20_000;
// Fotos mínimas por vuelta de la tira (≈ 210px c/u): suficiente para cubrir la columna izquierda
// de una TV de 1920px sin hueco antes de que empiece la copia del loop.
const PHOTO_STRIP_MIN_ITEMS = 8;
// Colores fijos para diferenciar planes a simple vista en el roster — a propósito distinto del
// acento único de marca del gym (que solo pinta UNA cosa): acá el objetivo es que dos socios
// con planes distintos se distingan de lejos, así que el color mismo es la señal.
// Sin verde #22c55e a propósito: es el mismo verde fijo del check-in confirmado (ver
// .tv-chip--confirmed en el SCSS) — un plan con ese verde se podía confundir con "ya vino",
// hallazgo real al revisar la paleta con la skill de UX. Cian en su lugar, lejos del azul y del
// verde de estado.
const PLAN_PALETTE = ['#f97316', '#3b82f6', '#06b6d4', '#ec4899', '#eab308', '#a855f7'];

// Una TV no tiene scroll — nada puede depender de más espacio del que existe. Lo que no cabe se
// PAGINA (carrusel) en vez de achicarse o esconderse tras un "+N": clases en curso de a 2,
// próximas de a 1, asistentes y agenda por páginas según el espacio medido. Cada página dura
// ~18 s: en una TV nadie puede apretar "siguiente", tiene que alcanzar a buscarse en la lista
// (antes 10 s: con 3-4 zonas cambiando a la vez, el dueño reportó que no alcanzaba a leer, 2026-10-18).
const HERO_PAGE_SIZE = 2;
const CAROUSEL_PAGE_MS = 18_000;
// Las paginas de asistentes (lista que no entra completa) rotan al doble de ritmo que el resto del carrusel (9 s contra
// 18 s, siempre en proporcion 2:1 para que los cambios caigan juntos): una pagina de hasta 12 nombres se lee en ~8 s;
// a 5 s (valor anterior) no alcanzaba. Costo: una tarjeta lateral de 16 asistentes completa su ciclo en ~54 s.
const ROSTER_PAGE_MS = 9_000;

// Tamaños de chip de asistente, de mayor a menor (rem). TvFitDirective mide el espacio real y
// elige el más grande en el que entran todos; si ni el chico alcanza, "+N". Los 3 primeros
// muestran nombre y apellido completos + píldora de plan; "s" muestra nombre + inicial del
// apellido y el plan solo como color del anillo (decisión explícita del usuario para espacios
// chicos).
const ROSTER_TIERS: FitTier[] = [
  { name: 'xl', w: 19, h: 5.2, gap: 1 },
  { name: 'l', w: 14.5, h: 4.6, gap: 0.85 },
  { name: 'm', w: 12.5, h: 3.2, gap: 0.6 },
  // w 10.5 (antes 9.5): el nombre sube a 14 px de piso y a 11 px de raíz (TV de 950x540) necesita más ancho.
  { name: 's', w: 10.5, h: 2.3, gap: 0.5 },
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
  // Tres alturas de la misma fila de una linea: sin los intermedios, 4 clases en una columna de 17 rem pasaban de 'xl' (no
  // entran) a 3.4 rem y dejaban un hueco vacio al pie de la tarjeta (visto en la TV real, 960x540, 2026-10-13).
  { name: 'row-l', w: 0, h: 4.6, gap: 0.5 },
  { name: 'row-l', w: 0, h: 4, gap: 0.5 },
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
  imports: [
    VersionTag,
    IonContent,
    NgTemplateOutlet,
    TvFitDirective,
  ],
  templateUrl: './tv-screen.html',
  styleUrl: './tv-screen.scss',
})
export class TvScreenPage implements OnDestroy {
  private readonly tvScreenService = inject(TvScreenService);

  protected readonly stage = signal<Stage>('pairing');
  protected readonly pairingCode = signal<string | null>(null);
  protected readonly schedule = signal<TvSchedule | null>(null);
  protected readonly photos = signal<TvPhoto[]>([]);
  private photosLoadedAt = 0;
  private photosLoading = false;
  protected readonly now = signal<Date>(new Date());

  // QR de asistencia — solo tiene sentido mientras hay al menos una clase en curso (ver
  // refreshCheckinQr). null = no mostrar nada, ni placeholder: no confundir "sin clase ahora"
  // con "todavía cargando".
  protected readonly checkinQrDataUrl = signal<string | null>(null);

  protected readonly logoSrc = computed(() => toLogoImgSrc(this.schedule()?.logoSvg));

  // Duplicada para que el scroll infinito (CSS puro, ver tv-screen.scss) cierre el loop sin
  // salto visible — mismo patrón ya probado en member.ts/member.scss.
  // Con pocas fotos (ej. 2), una vuelta no alcanza a cubrir el ancho de la columna y quedaba un
  // hueco a la derecha — se repite la lista hasta tener al menos PHOTO_STRIP_MIN_ITEMS por vuelta.
  protected readonly photoStrip = computed(() => {
    const list = this.photos();
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
  // Sin red desde el arranque: true pasados OFFLINE_AFTER_MS sin primera respuesta. Vuelve a false sola
  // cuando llega una respuesta buena (el sondeo nunca se detiene).
  protected readonly offline = signal(false);
  protected readonly lastAttemptLabel = signal('');
  // Momento (reloj real) del próximo reintento de vinculación; la cuenta regresiva de la pantalla de
  // error se deriva del reloj de 1 s existente → sin timer propio que limpiar.
  private readonly pairingRetryAt = signal(0);
  protected readonly pairingRetryIn = computed(() => {
    this.now(); // recomputa cada segundo (el cálculo usa Date.now()).
    return Math.max(0, Math.ceil((this.pairingRetryAt() - Date.now()) / 1000));
  });
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
  protected readonly rosterTick = computed(() => Math.floor(this.now().getTime() / ROSTER_PAGE_MS));

  // Sin clase en curso: "próxima clase" pasa a ocupar el lugar protagónico — mismo trato
  // completo que "ahora", es la información más útil en ese momento porque todavía se puede
  // reservar (feedback real del usuario: dejar esa área con un aviso era espacio muerto).
  protected readonly isIdle = computed(() => this.allCurrentBlocks().length === 0);

  // Tarjeta grande: SIEMPRE bloques de a lo más HERO_PAGE_SIZE clases (decisión del usuario —
  // con 3+ simultáneas, en vez de achicarlas todas, se pagina de a 2 y se turnan).
  private readonly heroPages = computed<TvBlockOccurrence[][]>(() =>
    chunk(this.isIdle() ? this.allNextBlocks() : this.allCurrentBlocks(), HERO_PAGE_SIZE),
  );
  // Modo compacto: con 2+ clases en el total (no por página) la tarjeta pierde barra de progreso y título de 2 líneas, y la
  // tira de fotos se oculta. Depende del TOTAL a propósito: si dependiera de las tarjetas de la página, el layout saltaría
  // entre "1/2" y "2/2" (la última página puede traer una sola).
  protected readonly heroCompact = computed(() => this.heroPages().reduce((n, page) => n + page.length, 0) >= 2);
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

  // Leyenda color→plan, UNA sola vez para toda la pantalla (no por tarjeta) — el color de cada
  // plan ya es estable en toda la TV (mismo planColorMap de arriba), así que repetirla en cada
  // clase en curso solo le comía espacio fijo al roster sin agregar información nueva (feedback
  // real del usuario). Se muestra SIEMPRE que haya al menos un plan en pantalla, incluso con uno solo: aunque
  // todos compartan plan, el dueño quiere ver cuál es (feedback real, 2026-10-18).
  protected readonly planLegend = computed<{ planId: number; planName: string; color: string }[]>(() => {
    const sched = this.schedule();
    if (!sched) return [];
    const groups = [...sched.current, ...sched.next, ...(sched.later ?? []), ...(sched.previous ? [sched.previous] : [])];
    const names = new Map<number, string>();
    groups.forEach((occ) =>
      occ.attendees.forEach((a) => {
        if (a.planId != null && a.planName && !names.has(a.planId)) {
          names.set(a.planId, a.planName);
        }
      }),
    );
    const colors = this.planColorMap();
    return [...names.entries()]
      .sort(([idA], [idB]) => idA - idB)
      .map(([planId, planName]) => ({ planId, planName, color: colors.get(planId) ?? PLAN_PALETTE[0] }));
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

  // Pantalla completa — ningún navegador permite activarla sin un gesto del usuario (clic, toque o
  // tecla), así que no se puede "forzar" al cargar: se entra en el PRIMER gesto (un clic o el OK del
  // control remoto) y, mientras no esté activa, queda un botón discreto para pedirla. Para una TV
  // permanente lo robusto es abrir Chrome en modo kiosco (--kiosk), que sobrevive a las recargas.
  protected readonly fullscreenSupported = typeof document.documentElement.requestFullscreen === 'function';
  protected readonly isFullscreen = signal(!!document.fullscreenElement);
  private readonly gestureEvents = ['pointerdown', 'keydown', 'touchend'] as const;
  protected readonly fullscreenHintVisible = signal(true);
  private hintTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly onFullscreenChange = (): void => {
    this.isFullscreen.set(!!document.fullscreenElement);
    if (document.fullscreenElement) this.clearHintTimer();
    else this.showFullscreenHint();
  };
  private readonly onFirstGesture = (): void => this.enterFullscreen();

  private clockOffsetMs = 0;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private kioskReloadTimer: ReturnType<typeof setTimeout> | null = null;
  private offlineTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Escala fluida: 1rem = 16px a 1536x864 (20px a 1080p) y proporcional al lado que limite (ancho o alto).
    // El SCSS entero está en rem, así que la pantalla es la misma composición en una TV de 720p,
    // 1080p, 4K o un navegador de TV a 960x540 (DPR 2) — antes solo escalaba con el ancho y en
    // pantallas bajas el encabezado se comía el alto del roster. TvFitDirective lee este mismo
    // font-size del <html>, así que sus tamaños de chip escalan parejo. Piso de 11px: por debajo
    // el texto deja de leerse (una TV real a 960x540 CSS px trae DPR 2, o sea 1080p físico).
    document.documentElement.style.fontSize = TV_ROOT_FONT_SIZE;
    if (this.fullscreenSupported) {
      document.addEventListener('fullscreenchange', this.onFullscreenChange);
      if (!document.fullscreenElement) this.showFullscreenHint();
      this.gestureEvents.forEach((name) => document.addEventListener(name, this.onFirstGesture, true));
    }
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

  /** Pide pantalla completa; si el navegador la rechaza (sin gesto válido) se reintenta en el próximo gesto. */
  protected enterFullscreen(): void {
    if (!this.fullscreenSupported || document.fullscreenElement) {
      return;
    }
    document.documentElement.requestFullscreen({ navigationUI: 'hide' }).then(
      () => this.stopListeningForGesture(),
      () => undefined,
    );
  }

  // Solo se fuerza en el primer gesto: si después alguien sale con Esc, se respeta (queda el botón).
  private stopListeningForGesture(): void {
    this.gestureEvents.forEach((name) => document.removeEventListener(name, this.onFirstGesture, true));
  }

  ngOnDestroy(): void {
    document.documentElement.style.removeProperty('font-size');
    document.removeEventListener('fullscreenchange', this.onFullscreenChange);
    this.stopListeningForGesture();
    if (this.clockTimer) clearInterval(this.clockTimer);
    if (this.pollTimer) clearTimeout(this.pollTimer);
    if (this.kioskReloadTimer) clearTimeout(this.kioskReloadTimer);
    this.clearOfflineTimer();
    this.clearHintTimer();
    clearThemeOverrides();
  }

  private showFullscreenHint(): void {
    this.clearHintTimer();
    this.fullscreenHintVisible.set(true);
    this.hintTimer = setTimeout(() => {
      this.hintTimer = null;
      this.fullscreenHintVisible.set(false);
    }, FULLSCREEN_HINT_MS);
  }

  private clearHintTimer(): void {
    if (this.hintTimer) clearTimeout(this.hintTimer);
    this.hintTimer = null;
  }

  private clearOfflineTimer(): void {
    if (this.offlineTimer) clearTimeout(this.offlineTimer);
    this.offlineTimer = null;
  }

  // HH:MM (hora de Chile) del último intento de conexión, para el aviso de "sin conexión".
  private markAttempt(): void {
    this.lastAttemptLabel.set(
      new Intl.DateTimeFormat('es-CL', {
        timeZone: 'America/Santiago',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date()),
    );
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
        this.markAttempt();
        this.pairingRetryAt.set(Date.now() + PAIRING_RETRY_MS);
        this.stage.set('error');
        this.pollTimer = setTimeout(() => this.startPairing(), PAIRING_RETRY_MS);
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

  // Pide la galería si nunca se bajó o pasaron 10 minutos. Si falla se deja la anterior y se reintenta en el próximo poll.
  private refreshPhotos(token: string): void {
    if (this.photosLoading || Date.now() - this.photosLoadedAt < PHOTOS_REFRESH_MS) {
      return;
    }
    this.photosLoading = true;
    this.tvScreenService.photos(token).subscribe({
      next: (photos) => {
        this.photos.set(photos);
        this.photosLoadedAt = Date.now();
        this.photosLoading = false;
      },
      error: () => {
        this.photosLoading = false;
      },
    });
  }

  private startSchedulePolling(token: string): void {
    // Un solo temporizador por arranque (no por reintento): si el sondeo no responde a tiempo, se avisa.
    this.clearOfflineTimer();
    this.offline.set(false);
    this.markAttempt();
    this.offlineTimer = setTimeout(() => {
      this.offlineTimer = null;
      if (this.stage() === 'loading') this.offline.set(true);
    }, OFFLINE_AFTER_MS);
    const poll = () => {
      this.tvScreenService.schedule(token).subscribe({
        next: (data) => {
          this.clockOffsetMs = new Date(data.serverTime).getTime() - Date.now();
          this.clearOfflineTimer();
          this.offline.set(false);
          this.lastSuccessfulPollAt.set(Date.now());
          this.schedule.set(data);
          this.stage.set('display');
          this.refreshPhotos(token);
          syncThemeOverrides(data.themeMode === 'LIGHT' ? 'LIGHT' : 'DARK', data.themeColor);
          // Solo tiene sentido marcar asistencia mientras hay una clase en curso — sin eso, se
          // limpia (no queda un QR "viejo" apuntando a un código ya vencido con nada que canjear).
          if (data.current.length > 0) {
            this.refreshCheckinQr(token);
          } else {
            this.checkinQrDataUrl.set(null);
          }
          this.pollTimer = setTimeout(poll, SCHEDULE_POLL_MS);
        },
        error: (err) => {
          if (err?.status === 404) {
            // La pantalla se desvinculó o pasó 90 días sin conectarse — hay que emparejar de nuevo.
            localStorage.removeItem(TV_TOKEN_KEY);
            this.clearOfflineTimer();
            this.offline.set(false);
            this.schedule.set(null);
            clearThemeOverrides();
            this.startPairing();
            return;
          }
          // Error transitorio de red: reintenta sin romper lo que ya está en pantalla.
          this.markAttempt();
          this.pollTimer = setTimeout(poll, SCHEDULE_POLL_MS);
        },
      });
    };
    poll();
  }

  // El código dura 45s en el backend (CHECKIN_CODE_TTL) y se pide uno nuevo en cada poll de
  // schedule (25s) — siempre hay margen antes de que venza. Si esta llamada puntual falla, el
  // QR anterior sigue en pantalla unos segundos más en vez de desaparecer de golpe.
  private refreshCheckinQr(screenToken: string): void {
    this.tvScreenService.checkinCode(screenToken).subscribe({
      next: (res) => {
        const url = `${location.origin}/checkin/${res.code}`;
        this.renderBrandedQr(url).then((dataUrl) => {
          if (dataUrl) {
            this.checkinQrDataUrl.set(dataUrl);
          }
        });
      },
      error: () => void 0,
    });
  }

  // QR "de marca": módulos en una variante oscura del acento del gimnasio (nunca el acento
  // crudo — muchos son colores claros tipo neón, ilegibles para una cámara) en vez del negro
  // genérico de cualquier QR, + el logo del propio gym (o el de mygym si no tiene) al centro.
  // Nivel de corrección 'H' es justamente lo que hace posible tapar el centro sin romper la
  // lectura — ver QR_LOGO_SIZE_RATIO arriba.
  private async renderBrandedQr(url: string): Promise<string | null> {
    const accent = this.schedule()?.themeColor ?? '#c6ff3d';
    const darkColor = ensureMinContrastColor(accent, '#ffffff', 7);
    const canvas = document.createElement('canvas');
    try {
      await qrToCanvas(canvas, url, {
        margin: 1,
        width: 320,
        errorCorrectionLevel: 'H',
        color: { dark: darkColor, light: '#ffffff' },
      });
    } catch {
      return null;
    }
    // Con el QR base ya dibujado, un fallo acá (logo raro, drawImage que no carga) nunca debe
    // tirar todo el QR abajo — antes esto no estaba cubierto y un error silencioso dejaba el QR
    // sin actualizar ninguna vuelta más (bug real reportado por el usuario: "no aparece").
    try {
      const ctx = canvas.getContext('2d');
      const logo = ctx ? await this.loadLogoImage() : null;
      if (ctx && logo) {
        const size = canvas.width * QR_LOGO_SIZE_RATIO;
        const pad = size * 0.16;
        const x = (canvas.width - size) / 2;
        const y = (canvas.height - size) / 2;
        ctx.fillStyle = '#ffffff';
        this.roundedRectPath(ctx, x - pad, y - pad, size + pad * 2, size + pad * 2, (size + pad * 2) * 0.2);
        ctx.fill();
        ctx.drawImage(logo, x, y, size, size);
      }
    } catch {
      // El QR base (sin logo) sigue siendo perfectamente escaneable — mejor eso que nada.
    }
    return canvas.toDataURL();
  }

  private loadLogoImage(): Promise<HTMLImageElement | null> {
    const src = toLogoImgSrc(this.schedule()?.logoSvg) ?? '/favicon.svg';
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = src;
    });
  }

  private roundedRectPath(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  // Hora y minutos; los segundos van aparte (mismo tamaño pero atenuados, ver .tv-clock__sec)
  // porque el reloj es funcional: la gente cronometra sus pausas entre series con él. Pedido explícito del usuario (antes se habían quitado porque titilaban en el tile
  // anterior; ahora son dígitos tabulares sin animación).
  protected clockLabel(): string {
    return new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(this.now());
  }

  protected secondsLabel(): string {
    return ':' + String(this.santiagoParts(this.now()).ss).padStart(2, '0');
  }

  // Separado de la fecha (día+mes) para el tile de dos líneas — antes era un solo string
  // "Lunes, 28 de septiembre".
  protected weekdayLabel(): string {
    const label = new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      weekday: 'long',
    }).format(this.now());
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected dayMonthLabel(): string {
    return new Intl.DateTimeFormat('es-CL', {
      timeZone: 'America/Santiago',
      day: 'numeric',
      month: 'long',
    }).format(this.now());
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

  // Null = la ocurrencia no es de hoy (no debería pasar para el hero en vivo, pero
  // remainingLabel ya contemplaba el caso devolviendo '').
  private remainingSeconds(occurrence: TvBlockOccurrence): number | null {
    const parts = this.santiagoParts(this.now());
    if (occurrence.classDate !== parts.dateIso) return null;
    const [eh, em] = occurrence.endTime.split(':').map(Number);
    const nowSec = parts.hh * 3600 + parts.mm * 60 + parts.ss;
    const endSec = eh * 3600 + em * 60;
    return endSec - nowSec;
  }

  protected remainingLabel(occurrence: TvBlockOccurrence): string {
    const remaining = this.remainingSeconds(occurrence);
    if (remaining === null) return '';
    if (remaining <= 0) return 'Termina ya';
    const mins = Math.floor(remaining / 60);
    if (mins < 1) return 'Último minuto';
    return `Quedan ${mins} min`;
  }

  // Caluga/barra de "en curso" pasan de acento de marca a un semáforo FIJO (no derivado del
  // acento del gimnasio) a medida que se acaba la clase — decisión de diseño: una señal de
  // estado de sistema tiene que verse igual en todos los gimnasios, si usara el acento variable
  // un gym con acento naranjo/rojo ya "se vería urgente" todo el rato. Umbrales en minutos
  // ABSOLUTOS (no % de duración): "quedan 5 min" transmite la misma urgencia en una clase de 30
  // que en una de 90.
  protected countdownUrgency(occurrence: TvBlockOccurrence): 'normal' | 'warning' | 'critical' {
    const remaining = this.remainingSeconds(occurrence);
    if (remaining === null) return 'normal';
    if (remaining <= 2 * 60) return 'critical';
    if (remaining <= 5 * 60) return 'warning';
    return 'normal';
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

  // Piso de alto del roster de una tarjeta lateral: con 3 o mas anotados, dos filas de chips chicos (en 1280x720 entran las dos
  // en "Clase anterior" y "Proxima clase"); con 1-2 anotados alcanza una fila.
  protected sideRosterMinRem(attendees: number): number {
    return attendees > 2 ? 2 * SIDEBAR_ROSTER_MIN_REM + 0.5 : SIDEBAR_ROSTER_MIN_REM;
  }

  protected occurrenceWeight(occurrence: TvBlockOccurrence): number {
    return Math.max(this.rosterNeedRem(occurrence.attendees.length) - SIDEBAR_ROSTER_MIN_REM, 0.1);
  }

  // "Clase anterior" ya pasó — quien mira la TV le importa mucho más "próxima clase". Sin este
  // descuento, un bloque anterior con más gente anotada que el siguiente le ganaba el alto extra
  // (reparto puramente proporcional a cuánta gente tiene cada uno) y "Próxima clase" quedaba con
  // chips más chicos y paginando más de lo necesario — reportado real con datos de prueba
  // (anterior con 15 mostraba más gente por página que próxima con solo 10).
  protected previousWeight(occurrence: TvBlockOccurrence): number {
    return this.occurrenceWeight(occurrence) * 0.5;
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

  // Etiqueta de 2-3 letras para .tv-chip__plan-tag — se saca el prefijo genérico "Plan" (si lo
  // tiene) para no colisionar dos planes distintos en las mismas 3 letras ("Plan Full" y
  // "Plan Básico" darían las dos "PLA"), y se sacan tildes porque a ese tamaño se pierden igual.
  protected planShort(planName: string): string {
    const withoutPrefix = planName.replace(/^plan\s+/i, '').trim() || planName;
    const letters = withoutPrefix
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-zA-Z]/g, '')
      .toUpperCase();
    return (letters || planName.toUpperCase()).slice(0, 3);
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
