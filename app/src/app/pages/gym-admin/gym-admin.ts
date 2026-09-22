import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonChip,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonText,
  IonTextarea,
  IonTitle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  barbellOutline,
  businessOutline,
  bulbOutline,
  calendarOutline,
  checkmarkCircleOutline,
  chevronBackOutline,
  chevronDownOutline,
  chevronForwardOutline,
  chevronUpOutline,
  closeCircleOutline,
  cloudUploadOutline,
  colorPaletteOutline,
  createOutline,
  helpCircleOutline,
  hourglassOutline,
  imagesOutline,
  logOutOutline,
  logoGoogle,
  megaphoneOutline,
  paperPlaneOutline,
  peopleOutline,
  personAddOutline,
  personCircleOutline,
  personOutline,
  pricetagOutline,
  refreshOutline,
  removeCircleOutline,
  searchOutline,
  settingsOutline,
  timeOutline,
  trashOutline,
} from 'ionicons/icons';
import { QuantityStepper } from '../../core/components/quantity-stepper/quantity-stepper';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { Gym } from '../../core/models/gym.model';
import { MemberService } from '../../core/services/member.service';
import {
  BlockOccurrenceAttendees,
  CreateGymBlockRequest,
  CreateGymPhotoRequest,
  CreateGymPlanRequest,
  DayOfWeek,
  GymBlock,
  GymPhoto,
  GymPlan,
  UpdateGymBlockRequest,
  UpdateGymPlanRequest,
  sortBlocksBySchedule,
} from '../../core/models/gym.model';
import { Attendee, InviteStatus, Member, MembershipStatus } from '../../core/models/member.model';
import { BloqueFormModal, DAYS } from '../admin/gyms/bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../admin/gyms/bloque-series-modal/bloque-series-modal';
import { PlanFormModal } from '../admin/gyms/plan-form-modal/plan-form-modal';
import { MarkPaidModal } from '../admin/gyms/mark-paid-modal/mark-paid-modal';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../core/utils/class-category';

registerClassCategoryIcons();
import {
  LIGHT_PALETTES,
  LightPaletteEntry,
  ThemeMode,
  clearThemeOverrides,
  deriveSurfaceTint,
  ensureMinContrastColor,
  syncThemeOverrides,
} from '../../core/utils/gym-theme';

addIcons({
  'time-outline': timeOutline,
  'calendar-outline': calendarOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'chevron-up-outline': chevronUpOutline,
  'chevron-down-outline': chevronDownOutline,
  'people-outline': peopleOutline,
  'paper-plane-outline': paperPlaneOutline,
  'person-add-outline': personAddOutline,
  'person-circle-outline': personCircleOutline,
  'color-palette-outline': colorPaletteOutline,
  'pricetag-outline': pricetagOutline,
  'refresh-outline': refreshOutline,
  'remove-circle-outline': removeCircleOutline,
  'search-outline': searchOutline,
  'business-outline': businessOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'create-outline': createOutline,
  'trash-outline': trashOutline,
  'bulb-outline': bulbOutline,
  'megaphone-outline': megaphoneOutline,
  'images-outline': imagesOutline,
  'log-out-outline': logOutOutline,
  'settings-outline': settingsOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'hourglass-outline': hourglassOutline,
  'close-circle-outline': closeCircleOutline,
  'help-circle-outline': helpCircleOutline,
  'person-outline': personOutline,
  'logo-google': logoGoogle,
  'barbell-outline': barbellOutline,
});

type Status = 'idle' | 'loading' | 'saving' | 'error';
type Section = 'general' | 'blocks' | 'plans' | 'members' | 'branding' | 'history';

// Segundo eje de filtro para la grilla de horarios (además del día) —
// pedido del usuario tras encontrar la fusión de bloques consecutivos poco
// intuitiva; en vez de agrupar visualmente, se deja la grilla como estaba
// y se agrega este filtro por franja para acotar cuántas tarjetas se ven
// a la vez. Los cortes (12:00, 18:00) son un criterio de negocio simple,
// no vienen de ninguna configuración del gimnasio.
type TimeBand = 'AM' | 'PM' | 'NIGHT';

const TIME_BAND_OPTIONS: { value: TimeBand; label: string; hint: string }[] = [
  { value: 'AM', label: 'Mañana', hint: 'Bloques que empiezan antes de las 12:00' },
  { value: 'PM', label: 'Tarde', hint: 'Bloques que empiezan entre las 12:00 y las 18:00' },
  { value: 'NIGHT', label: 'Noche', hint: 'Bloques que empiezan desde las 18:00' },
];
const TIME_BAND_ALL_HINT = 'Mostrar bloques de cualquier horario';

function timeBandOf(startTime: string): TimeBand {
  if (startTime < '12:00') {
    return 'AM';
  }
  return startTime < '18:00' ? 'PM' : 'NIGHT';
}

// Un bloque es una plantilla semanal (sin fecha) — "quién reservó" necesita
// una fecha real. Como el admin no navega un calendario acá, se usa la
// PRÓXIMA ocurrencia real de ese día de semana (hoy mismo si coincide) como
// fecha por defecto, mismo criterio de huso horario (America/Santiago) que
// ya usa member.ts para todo lo relacionado a fechas de clases.
const DAY_OF_WEEK_INDEX: Record<DayOfWeek, number> = {
  SUNDAY: 0,
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
};

function pad2(n: number): string {
  return n.toString().padStart(2, '0');
}

function todayIsoDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

function nextOccurrenceDate(dayOfWeek: DayOfWeek): string {
  const todayIso = todayIsoDate();
  const [y, m, d] = todayIso.split('-').map(Number);
  const today = new Date(y, m - 1, d);
  const diff = (DAY_OF_WEEK_INDEX[dayOfWeek] - today.getDay() + 7) % 7;
  const target = new Date(y, m - 1, d + diff);
  return `${target.getFullYear()}-${pad2(target.getMonth() + 1)}-${pad2(target.getDate())}`;
}

const SECTION_LABELS: Record<Section, string> = {
  general: 'General',
  blocks: 'Horarios',
  plans: 'Planes',
  members: 'Socios',
  branding: 'Marca',
  history: 'Historial',
};

// Índice → DayOfWeek, para saber qué día de semana cae una fecha elegida a
// mano en el selector de Historial. Construido a partir de componentes
// y/m/d explícitos (nunca `new Date(isoString)`) para no repetir el bug de
// interpretación UTC ya encontrado con nextOccurrenceDate/formatDate.
const DAY_OF_WEEK_BY_INDEX: DayOfWeek[] = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
];

function dayOfWeekOfDate(dateIso: string): DayOfWeek {
  const [y, m, d] = dateIso.split('-').map(Number);
  return DAY_OF_WEEK_BY_INDEX[new Date(y, m - 1, d).getDay()];
}

function addDaysIso(dateIso: string, days: number): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

function mondayOfWeek(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const dow = new Date(y, m - 1, d).getDay();
  return addDaysIso(dateIso, dow === 0 ? -6 : 1 - dow);
}

function historyDayLabel(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const label = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'short' }).format(
    new Date(y, m - 1, d),
  );
  return label.charAt(0).toUpperCase() + label.slice(1);
}

interface HistoryDaySummary {
  date: string;
  dayLabel: string;
  blocks: GymBlock[];
  totalAttendees: number;
}

// Rotan igual que las citas motivacionales de /member, pero con un tono de
// negocio en vez de motivacional — un guiño sutil, no un hero completo.
const ADMIN_TIPS = [
  'Los planes con precio y cupo claros retienen más socios que los acordados "de palabra".',
  'Un bloque casi lleno es una señal: quizás conviene abrir otro horario similar.',
  'Los socios que reservan seguido son, en general, los que menos se dan de baja.',
  'Revisa tus planes cada cierto tiempo — lo que funcionó al abrir no siempre es lo óptimo un año después.',
];

interface Palette {
  key: string;
  label: string;
  hex: string;
  contrast: string;
}

// Mirrors GymPalette.java (backend) — if one changes, update the other.
const PALETTES: Palette[] = [
  { key: 'lime', label: 'Lima', hex: '#c6ff3d', contrast: '#1a2b00' },
  { key: 'blue', label: 'Azul eléctrico', hex: '#3da5ff', contrast: '#001a33' },
  { key: 'rose', label: 'Coral', hex: '#ff5d73', contrast: '#330008' },
  { key: 'gold', label: 'Ámbar', hex: '#ffb23d', contrast: '#331d00' },
  { key: 'emerald', label: 'Esmeralda', hex: '#2de6a0', contrast: '#00291a' },
  { key: 'violet', label: 'Violeta', hex: '#b98bff', contrast: '#1c0d33' },
  { key: 'cyan', label: 'Cian', hex: '#3de6e6', contrast: '#002626' },
  { key: 'orange', label: 'Naranja', hex: '#ff7a3d', contrast: '#331500' },
  { key: 'indigo', label: 'Índigo', hex: '#6d7bff', contrast: '#05073d' },
  { key: 'fuchsia', label: 'Fucsia', hex: '#ff5cb8', contrast: '#330019' },
  { key: 'turquoise', label: 'Turquesa', hex: '#2dd4bf', contrast: '#00211c' },
  { key: 'plum', label: 'Ciruela', hex: '#c15aff', contrast: '#24003d' },
];

function randomSample<T>(items: T[], count: number): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, count);
}

const MAX_LOGO_DIMENSION = 256;
const ACCEPTED_LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];
const MAX_PHOTO_DIMENSION = 1600;
const ACCEPTED_PHOTO_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_PHOTOS = 8;

/** Contraste WCAG simple para colores fuera de las 12 paletas curadas (color libre). */
function computeContrast(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const linear = (c: number) => (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
  const luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
  return luminance > 0.35 ? '#111111' : '#ffffff';
}

const THEMED_ROOT_PROPERTIES = [
  '--ion-color-primary',
  '--ion-color-primary-contrast',
  '--brand-accent',
  '--brand-accent-contrast',
  '--gym-panel-bg',
  '--gym-panel-card',
  '--brand-accent-text-safe',
] as const;

@Component({
  selector: 'app-gym-admin',
  imports: [
    ReactiveFormsModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonIcon,
    IonItem,
    IonLabel,
    IonInput,
    IonList,
    IonBadge,
    IonChip,
    IonModal,
    IonSegment,
    IonSegmentButton,
    IonText,
    IonTextarea,
    BloqueFormModal,
    BloqueSeriesModal,
    PlanFormModal,
    MarkPaidModal,
    QuantityStepper,
    IonSpinner,
  ],
  templateUrl: './gym-admin.html',
  styleUrl: './gym-admin.scss',
})
export class GymAdmin implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly memberService = inject(MemberService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);

  protected readonly status = signal<Status>('idle');
  protected readonly section = signal<Section>('general');
  protected readonly sectionLabel = computed(() => SECTION_LABELS[this.section()]);
  protected readonly adminFirstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'admin');
  protected readonly tip = ADMIN_TIPS[Math.floor(Math.random() * ADMIN_TIPS.length)];
  protected readonly gym = signal<Gym | null>(null);
  protected readonly gymName = signal('');
  protected readonly gymSlug = signal<string | null>(null);
  protected readonly gymLoaded = signal(false);
  protected readonly blocks = signal<GymBlock[]>([]);
  // Antes se mostraban TODOS los bloques en una sola lista larga — con varios
  // días configurados, el scroll se volvía interminable (reportado por el
  // usuario). `null` = "Todos los días".
  protected readonly selectedDay = signal<DayOfWeek | null>(null);
  protected readonly days = DAYS;
  protected readonly selectedTimeBand = signal<TimeBand | null>(null);
  protected readonly timeBandOptions = TIME_BAND_OPTIONS;
  protected readonly timeBandAllHint = TIME_BAND_ALL_HINT;
  protected readonly filteredBlocks = computed(() => {
    const day = this.selectedDay();
    const band = this.selectedTimeBand();
    return this.blocks().filter(
      (b) => (!day || b.dayOfWeek === day) && (!band || timeBandOf(b.startTime) === band),
    );
  });
  // Quién reservó en un bloque — pedido explícito del usuario. Cada bloque
  // es una plantilla semanal sin fecha, así que hace falta una fecha real
  // para consultar (próxima ocurrencia en Horarios, la elegida a mano en
  // Historial) — la clave de cache incluye la fecha para que ver el mismo
  // bloque en dos fechas distintas no pise una caché con la otra. Acordeón,
  // un solo bloque expandido a la vez, se pide al backend recién al expandir.
  protected readonly expandedAttendeesKey = signal<string | null>(null);
  protected readonly attendeesByKey = signal<Record<string, Attendee[]>>({});
  protected readonly loadingAttendeesKey = signal<string | null>(null);

  // Historial: mismo mecanismo que "Ver quién reservó" en Horarios, pero con
  // una fecha elegida a mano en vez de la próxima ocurrencia automática —
  // pedido explícito del usuario para poder mirar cualquier clase pasada.
  protected readonly historyDate = signal(
    new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date()),
  );
  protected readonly historyLoading = signal(false);
  protected readonly expandedHistoryDay = signal<string | null>(null);

  // Semana en vez de un solo día — pedido explícito del usuario tras ver la
  // versión de un día ("¿podría ser una semana?"). `historyDate` sigue
  // siendo la fecha elegida a mano (ancla), la semana se deriva de ahí
  // (lunes a domingo) para poder navegar con flechas sin perder el punto
  // de partida elegido.
  protected readonly historyWeekStart = computed(() => mondayOfWeek(this.historyDate()));
  protected readonly historyWeekDates = computed(() => {
    const start = this.historyWeekStart();
    return Array.from({ length: 7 }, (_, i) => addDaysIso(start, i));
  });
  protected readonly historyWeekLabel = computed(() => {
    const dates = this.historyWeekDates();
    const fmt = (iso: string) => {
      const [y, m, d] = iso.split('-').map(Number);
      return new Intl.DateTimeFormat('es-CL', { day: 'numeric', month: 'short' }).format(new Date(y, m - 1, d));
    };
    return `${fmt(dates[0])} – ${fmt(dates[6])}`;
  });

  // Pedido explícito del usuario: en Historial solo importan las clases donde
  // se anotó alguien — una clase sin reservas no aporta nada para revisar.
  // Colapsado por día (resumen + conteo) para no repetir la sobrecarga de
  // información ya resuelta en Horarios al mostrar 7 días de una.
  protected readonly historyWeekSummary = computed<HistoryDaySummary[]>(() => {
    const cache = this.attendeesByKey();
    return this.historyWeekDates().map((date) => {
      const dow = dayOfWeekOfDate(date);
      const blocks = this.blocks()
        .filter((b) => b.dayOfWeek === dow)
        .filter((b) => (cache[this.attendeesKey(b.id, date)] ?? []).length > 0)
        .sort((a, b) => a.startTime.localeCompare(b.startTime));
      const totalAttendees = blocks.reduce(
        (sum, b) => sum + (cache[this.attendeesKey(b.id, date)] ?? []).length,
        0,
      );
      return { date, dayLabel: historyDayLabel(date), blocks, totalAttendees };
    });
  });
  protected readonly historyWeekHasBookings = computed(() =>
    this.historyWeekSummary().some((day) => day.blocks.length > 0),
  );

  // Buscador de reservas futuras por nombre de socio — pedido explícito del usuario para no
  // tener que recorrer bloque por bloque/semana por semana buscando a alguien puntual. No es
  // un filtro client-side: los rosters no están cargados completos en memoria (Horarios solo
  // trae la próxima ocurrencia de cada bloque, Historial solo la semana visible), así que un
  // buscador real necesita pedirle al backend (ver ReservationService.searchUpcomingReservations).
  // Mientras hay una búsqueda activa (≥2 caracteres), reemplaza la vista semanal normal de
  // Historial por la lista de coincidencias.
  protected readonly reservationSearchQuery = signal('');
  protected readonly reservationSearchResults = signal<BlockOccurrenceAttendees[] | null>(null);
  protected readonly reservationSearchLoading = signal(false);
  private reservationSearchTimeout: ReturnType<typeof setTimeout> | null = null;

  protected readonly reservationSearchBlocks = computed(() => {
    const results = this.reservationSearchResults();
    if (!results) {
      return null;
    }
    const blocksById = new Map(this.blocks().map((b) => [b.id, b]));
    return results
      .map((occurrence) => {
        const block = blocksById.get(occurrence.gymBlockId);
        return block ? { block, date: occurrence.classDate, attendees: occurrence.attendees } : null;
      })
      .filter((entry): entry is { block: GymBlock; date: string; attendees: Attendee[] } => entry !== null)
      .sort((a, b) => a.date.localeCompare(b.date) || a.block.startTime.localeCompare(b.block.startTime));
  });

  protected readonly plans = signal<GymPlan[]>([]);
  protected readonly members = signal<Member[]>([]);
  protected readonly activeMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'ACTIVE').length);
  protected readonly expiringSoonMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'EXPIRING_SOON').length);
  protected readonly expiredMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'EXPIRED').length);
  protected readonly unpaidMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'UNPAID').length);
  protected readonly memberStatusFilter = signal<MembershipStatus | null>(null);
  // Antes memberStatusFilter/memberInviteFilter eran dos ejes independientes que se
  // combinaban con AND (podían dar 0 socios sin que se viera obvio por qué) — las 6 calugas
  // se ven como un único grupo de filtro, así que ahora click en cualquiera reemplaza
  // cualquier filtro activo del otro eje, nunca se suman. Ver viewMembersByStatus/ByInvite.
  protected readonly invitedPendingMembers = computed(() => this.members().filter((m) => m.inviteStatus === 'PENDING').length);
  protected readonly invitedRegisteredMembers = computed(
    () => this.members().filter((m) => m.inviteStatus === 'REGISTERED').length,
  );
  protected readonly memberInviteFilter = signal<InviteStatus>(null);
  protected readonly filteredMembers = computed(() => {
    const statusFilter = this.memberStatusFilter();
    const inviteFilter = this.memberInviteFilter();
    return this.members().filter(
      (m) => (!statusFilter || m.membershipStatus === statusFilter) && (!inviteFilter || m.inviteStatus === inviteFilter),
    );
  });
  protected readonly isModalOpen = signal(false);
  protected readonly editingBlock = signal<GymBlock | null>(null);
  protected readonly isSeriesModalOpen = signal(false);
  protected readonly seriesCreating = signal(false);
  protected readonly isPlanModalOpen = signal(false);
  protected readonly editingPlan = signal<GymPlan | null>(null);

  protected readonly logoSvg = signal<string | null>(null);
  protected readonly isRasterLogo = computed(() => (this.logoSvg() ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.logoSvg();
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });
  protected readonly logoUploading = signal(false);

  protected readonly themeColor = signal<string>(PALETTES[0].hex);
  // 'DARK' (default, acento libre) o 'LIGHT' (limitado a LIGHT_PALETTES) — también decide
  // qué grilla de swatches se muestra (ver template). Cuando cambia junto con themeColor,
  // el effect() de más abajo sincroniza los tokens base globales (ver gym-theme.ts).
  protected readonly themeMode = signal<ThemeMode>('DARK');
  protected readonly lightPalettes = LIGHT_PALETTES;
  protected readonly themeContrast = computed(() => {
    const hex = this.themeColor();
    if (this.themeMode() === 'LIGHT') {
      return LIGHT_PALETTES.find((p) => p.hex.toLowerCase() === hex.toLowerCase())?.contrast ?? '#FFFFFF';
    }
    const match = PALETTES.find((p) => p.hex.toLowerCase() === hex.toLowerCase());
    return match?.contrast ?? computeContrast(hex);
  });
  protected readonly themeSurface = computed(() => deriveSurfaceTint(this.themeColor(), this.themeMode()));
  // El acento libre a veces no alcanza 4.5:1 como texto plano sobre la tarjeta
  // (ej. el índigo real de Fortis mide ~4.07:1) — esta variante SOLO se usa
  // donde el acento pinta texto, nunca donde pinta un fondo sólido.
  protected readonly themeAccentTextSafe = computed(() =>
    ensureMinContrastColor(this.themeColor(), this.themeSurface().card),
  );
  protected readonly paletteOptions = signal<Palette[]>(randomSample(PALETTES, 4));

  protected readonly memberForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  protected readonly identityForm = new FormGroup({
    tagline: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(160)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(600)] }),
    instagramUrl: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    whatsappNumber: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    cancellationWindowHours: new FormControl(2, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(72)],
    }),
  });
  protected readonly identitySaving = signal(false);

  protected readonly photos = signal<GymPhoto[]>([]);
  protected readonly photoUploading = signal(false);
  protected readonly maxPhotos = MAX_PHOTOS;

  constructor() {
    this.loadGym();
    this.loadBlocks();
    this.loadPlans();
    this.loadMembers();
    this.loadPhotos();

    // Ionic overlays (the ion-select popup, ion-alert, ion-toast) are
    // portaled to the top of the DOM, outside <ion-content>/<ion-modal> —
    // setting the theme vars only there never reaches them. Custom
    // properties inherit through the whole document, so set them on
    // <html> instead while this page is active, and clear them on the way
    // out so they don't leak into other routes (login, /admin/gyms, etc.).
    effect(() => {
      const color = this.themeColor();
      const contrast = this.themeContrast();
      const surface = this.themeSurface();
      const root = document.documentElement.style;
      root.setProperty('--ion-color-primary', color);
      root.setProperty('--ion-color-primary-contrast', contrast);
      root.setProperty('--brand-accent', color);
      root.setProperty('--brand-accent-contrast', contrast);
      root.setProperty('--gym-panel-bg', surface.bg);
      root.setProperty('--gym-panel-card', surface.card);
      root.setProperty('--brand-accent-text-safe', this.themeAccentTextSafe());
      // Modo claro necesita además los tokens base globales (--brand-ink, --ion-color-danger,
      // el step-ramp de Ionic) que --gym-panel-bg/card no cubren — ver gym-theme.ts.
      syncThemeOverrides(this.themeMode(), color);
    });

    // Los bloques llegan async (loadBlocks) — si el admin entra a Historial
    // antes de que respondan, hay que reintentar la carga de asistentes en
    // cuanto historyDayBlocks() deje de estar vacío, no solo al cambiar de
    // fecha o sección.
    effect(() => {
      this.blocks();
      this.historyDate();
      if (this.section() === 'history') {
        this.loadHistoryAttendees();
      }
    });
  }

  ngOnDestroy(): void {
    const root = document.documentElement.style;
    for (const property of THEMED_ROOT_PROPERTIES) {
      root.removeProperty(property);
    }
    clearThemeOverrides();
  }

  protected setSection(section: Section): void {
    this.section.set(section);
    if (section === 'history') {
      this.loadHistoryAttendees();
    }
  }

  protected viewMembersByStatus(status: MembershipStatus): void {
    this.memberStatusFilter.set(status);
    this.memberInviteFilter.set(null);
    this.section.set('members');
  }

  protected clearMemberStatusFilter(): void {
    this.memberStatusFilter.set(null);
  }

  protected viewMembersByInvite(status: Exclude<InviteStatus, null>): void {
    this.memberInviteFilter.set(status);
    this.memberStatusFilter.set(null);
    this.section.set('members');
  }

  protected clearMemberInviteFilter(): void {
    this.memberInviteFilter.set(null);
  }

  protected inviteStatusLabel(status: Exclude<InviteStatus, null>): string {
    return status === 'PENDING' ? 'Invitado' : 'Invitado registrado';
  }

  protected dayLabel(day: DayOfWeek): string {
    return DAYS.find((d) => d.value === day)?.label ?? day;
  }

  // La fila de bloque nunca mostraba la categoría (siempre el mismo ícono
  // genérico) ni el nombre del instructor — el admin no tenía forma de
  // confirmar visualmente que esos campos se habían guardado. Reportado por
  // el usuario 2026-09-13 al no ver reflejado un cambio de categoría/
  // instructor en la grilla (el dato SÍ se guardaba, solo no se mostraba).
  protected categoryIcon(category: string | null): string {
    return resolveClassCategoryIcon(category);
  }

  protected selectDayFilter(day: DayOfWeek | null): void {
    this.selectedDay.set(day);
  }

  protected selectTimeBand(band: TimeBand | null): void {
    this.selectedTimeBand.set(band);
  }

  // Cada chip cuenta contra el OTRO filtro activo (día vs. franja), no
  // contra el total sin filtrar — si el admin ya eligió "Lunes", el chip
  // "Mañana" debe mostrar cuántos bloques de LUNES son de mañana, no el
  // total de bloques de mañana en toda la semana.
  protected blockCountForDay(day: DayOfWeek | null): number {
    const band = this.selectedTimeBand();
    return this.blocks().filter((b) => (!day || b.dayOfWeek === day) && (!band || timeBandOf(b.startTime) === band))
      .length;
  }

  protected blockCountForTimeBand(band: TimeBand | null): number {
    const day = this.selectedDay();
    return this.blocks().filter((b) => (!day || b.dayOfWeek === day) && (!band || timeBandOf(b.startTime) === band))
      .length;
  }

  protected emptyBlocksMessage(): string {
    if (this.blocks().length === 0) {
      return 'Todavía no hay bloques configurados.';
    }
    const day = this.selectedDay();
    const band = this.selectedTimeBand();
    const bandLabel = band ? this.timeBandOptions.find((b) => b.value === band)?.label.toLowerCase() : null;
    if (day && bandLabel) {
      return `No hay bloques de ${bandLabel} para ${this.dayLabel(day)}.`;
    }
    if (day) {
      return `No hay bloques para ${this.dayLabel(day)}.`;
    }
    if (bandLabel) {
      return `No hay bloques de ${bandLabel}.`;
    }
    return 'Todavía no hay bloques configurados.';
  }

  // OJO: nextOccurrenceDate() devuelve una fecha calendario pura ("YYYY-MM-DD",
  // sin hora/huso) — pasarla por formatDate() (que hace `new Date(value)`) se
  // interpreta como medianoche UTC y se corría un día para atrás al formatear
  // en un huso horario negativo (ej. America/Santiago). Reordenar el string a
  // mano evita construir un Date del todo.
  protected nextOccurrenceLabel(block: GymBlock): string {
    const [y, m, d] = nextOccurrenceDate(block.dayOfWeek).split('-');
    return `${d}-${m}-${y}`;
  }

  private attendeesKey(blockId: number, date: string): string {
    return `${blockId}_${date}`;
  }

  protected isAttendeesExpanded(blockId: number, date: string): boolean {
    return this.expandedAttendeesKey() === this.attendeesKey(blockId, date);
  }

  protected isAttendeesLoading(blockId: number, date: string): boolean {
    return this.loadingAttendeesKey() === this.attendeesKey(blockId, date);
  }

  protected attendeesForDate(blockId: number, date: string): Attendee[] {
    return this.attendeesByKey()[this.attendeesKey(blockId, date)] ?? [];
  }

  protected toggleAttendeesFor(blockId: number, date: string): void {
    const key = this.attendeesKey(blockId, date);
    if (this.expandedAttendeesKey() === key) {
      this.expandedAttendeesKey.set(null);
      return;
    }
    this.expandedAttendeesKey.set(key);
    if (key in this.attendeesByKey()) {
      return;
    }
    this.loadingAttendeesKey.set(key);
    this.gymService.getMyGymBlockAttendees(blockId, date).subscribe({
      next: (attendees) => {
        this.attendeesByKey.update((map) => ({ ...map, [key]: attendees }));
        this.loadingAttendeesKey.set(null);
      },
      error: () => this.loadingAttendeesKey.set(null),
    });
  }

  // Wrappers para la plantilla (Angular no puede llamar funciones sueltas
  // del módulo, solo métodos/propiedades del componente).
  protected attendeesFor(block: GymBlock): Attendee[] {
    return this.attendeesForDate(block.id, nextOccurrenceDate(block.dayOfWeek));
  }

  protected toggleAttendees(block: GymBlock): void {
    this.toggleAttendeesFor(block.id, nextOccurrenceDate(block.dayOfWeek));
  }

  protected isBlockAttendeesExpanded(block: GymBlock): boolean {
    return this.isAttendeesExpanded(block.id, nextOccurrenceDate(block.dayOfWeek));
  }

  protected isBlockAttendeesLoading(block: GymBlock): boolean {
    return this.isAttendeesLoading(block.id, nextOccurrenceDate(block.dayOfWeek));
  }

  // Necesaria en la plantilla para pasarle la fecha exacta a cancelReservation() —
  // toggleAttendees/attendeesFor la calculan internamente pero no la exponen.
  protected blockOccurrenceDate(block: GymBlock): string {
    return nextOccurrenceDate(block.dayOfWeek);
  }

  // A diferencia de Horarios (siempre "hoy o la próxima ocurrencia"), acá
  // conviven 7 fechas distintas a la vez en pantalla — cada wrapper recibe
  // la fecha del día puntual que el admin está mirando.
  protected historyAttendeesFor(block: GymBlock, date: string): Attendee[] {
    return this.attendeesForDate(block.id, date);
  }

  protected toggleHistoryAttendees(block: GymBlock, date: string): void {
    this.toggleAttendeesFor(block.id, date);
  }

  protected isHistoryAttendeesExpanded(block: GymBlock, date: string): boolean {
    return this.isAttendeesExpanded(block.id, date);
  }

  protected isHistoryAttendeesLoading(block: GymBlock, date: string): boolean {
    return this.isAttendeesLoading(block.id, date);
  }

  protected toggleHistoryDay(date: string): void {
    this.expandedHistoryDay.update((current) => (current === date ? null : date));
  }

  protected isHistoryDayExpanded(date: string): boolean {
    return this.expandedHistoryDay() === date;
  }

  protected setHistoryDate(date: string): void {
    this.historyDate.set(date);
    this.expandedAttendeesKey.set(null);
    this.expandedHistoryDay.set(null);
    this.loadHistoryAttendees();
  }

  protected shiftHistoryWeek(direction: -1 | 1): void {
    this.setHistoryDate(addDaysIso(this.historyWeekStart(), direction * 7));
  }

  // Antes esto era 1 request por cada bloque×día de la semana en paralelo (fan-out de
  // N llamadas contra el pool de conexiones del backend — con pocos bloques ya alcanzaba
  // para sentirse lento, ver SKILL.md). Una sola llamada trae toda la semana. Se cachea por
  // rango de fechas (no por key individual, porque el batch solo devuelve ocurrencias CON
  // asistentes — una semana ya pedida no vuelve a pedirse aunque el effect se re-dispare).
  private readonly historyLoadedRanges = new Set<string>();

  private loadHistoryAttendees(): void {
    const dates = this.historyWeekDates();
    const from = dates[0];
    const to = dates[dates.length - 1];
    const rangeKey = `${from}_${to}`;
    if (this.historyLoadedRanges.has(rangeKey)) {
      return;
    }
    this.historyLoadedRanges.add(rangeKey);
    this.historyLoading.set(true);
    this.gymService.getMyHistoryAttendees(from, to).subscribe({
      next: (occurrences) => {
        this.attendeesByKey.update((map) => {
          const next = { ...map };
          for (const occurrence of occurrences) {
            next[this.attendeesKey(occurrence.gymBlockId, occurrence.classDate)] = occurrence.attendees;
          }
          return next;
        });
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false),
    });
  }

  protected onReservationSearchInput(value: string): void {
    this.reservationSearchQuery.set(value);
    if (this.reservationSearchTimeout) {
      clearTimeout(this.reservationSearchTimeout);
    }
    const trimmed = value.trim();
    if (trimmed.length < 2) {
      this.reservationSearchResults.set(null);
      this.reservationSearchLoading.set(false);
      return;
    }
    // Debounce manual — no hace falta rxjs-interop para un solo input, se sigue el estilo
    // pragmático (signals + subscribe directo) ya usado en todo este archivo.
    this.reservationSearchTimeout = setTimeout(() => this.runReservationSearch(trimmed), 300);
  }

  private runReservationSearch(query: string): void {
    this.reservationSearchLoading.set(true);
    this.gymService.searchMyReservations(query).subscribe({
      next: (results) => {
        this.reservationSearchResults.set(results);
        this.reservationSearchLoading.set(false);
      },
      error: () => this.reservationSearchLoading.set(false),
    });
  }

  // El botón "Cancelar" de admin solo tiene sentido para clases que todavía no pasaron —
  // cancelar algo que ya ocurrió reescribiría historial (mismo criterio que ya valida el
  // backend en ReservationService.cancelAsAdmin).
  protected canCancelOccurrence(date: string): boolean {
    return date >= todayIsoDate();
  }

  // Vía de urgencia pedida explícitamente por el usuario: cancela la reserva de CUALQUIER
  // socio, sin la ventana de horas que le aplica a la auto-cancelación del socio — para cuando
  // llama al admin porque no llega a cancelar solo y no quiere que la clase le cuente como usada.
  protected async cancelReservation(attendee: Attendee, blockId: number, date: string): Promise<void> {
    const confirmed = await this.confirmAction(
      'Cancelar reserva',
      `¿Cancelar la reserva de ${attendee.name} para esta clase? Se libera el cupo.`,
      'Cancelar reserva',
    );
    if (!confirmed) {
      return;
    }
    this.gymService.cancelReservation(attendee.reservationId).subscribe({
      next: () => {
        const key = this.attendeesKey(blockId, date);
        this.attendeesByKey.update((map) => ({
          ...map,
          [key]: (map[key] ?? []).filter((a) => a.reservationId !== attendee.reservationId),
        }));
        this.reservationSearchResults.update((results) =>
          results
            ? results
                .map((occurrence) =>
                  occurrence.gymBlockId === blockId && occurrence.classDate === date
                    ? { ...occurrence, attendees: occurrence.attendees.filter((a) => a.reservationId !== attendee.reservationId) }
                    : occurrence,
                )
                .filter((occurrence) => occurrence.attendees.length > 0)
            : results,
        );
        this.showToast('Reserva cancelada.');
      },
      error: () => this.showToast('No pudimos cancelar la reserva. Intenta nuevamente.', 'danger'),
    });
  }

  protected formatClp(value: number): string {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(value);
  }

  protected formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    return new Intl.DateTimeFormat('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(
      new Date(value),
    );
  }

  protected quotaLabel(plan: GymPlan): string {
    return plan.monthlyClasses === null ? 'Libre (ilimitado)' : `${plan.monthlyClasses} clases/mes`;
  }

  protected reshufflePalettes(): void {
    this.paletteOptions.set(randomSample(PALETTES, 4));
  }

  protected surfaceFor(palette: Palette): { bg: string; card: string } {
    return deriveSurfaceTint(palette.hex, 'DARK');
  }

  // Cambiar el segment Oscuro/Claro solo cambia qué grilla se ve — no guarda nada hasta
  // que se elige un swatch puntual (mismo criterio que ya existía: click = guarda ya).
  protected setThemeModeView(mode: ThemeMode): void {
    this.themeMode.set(mode);
  }

  protected readonly themeSaving = signal(false);

  protected selectPalette(palette: Palette): void {
    this.selectColor(palette.hex, 'DARK');
  }

  protected selectLightPalette(palette: LightPaletteEntry): void {
    this.selectColor(palette.hex, 'LIGHT');
  }

  protected onCustomColorInput(event: Event): void {
    const hex = (event.target as HTMLInputElement).value;
    this.selectColor(hex, 'DARK');
  }

  private selectColor(hex: string, mode: ThemeMode): void {
    if (this.themeSaving()) {
      return;
    }
    const previousColor = this.themeColor();
    const previousMode = this.themeMode();
    this.themeColor.set(hex);
    this.themeMode.set(mode);
    this.themeSaving.set(true);
    this.gymService.updateMyTheme({ themeColor: hex, themeMode: mode }).subscribe({
      next: () => this.themeSaving.set(false),
      error: () => {
        this.themeColor.set(previousColor);
        this.themeMode.set(previousMode);
        this.themeSaving.set(false);
        this.showToast('No pudimos guardar el color. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected saveIdentity(): void {
    if (this.identityForm.invalid) {
      return;
    }
    const raw = this.identityForm.getRawValue();
    this.identitySaving.set(true);
    this.gymService
      .updateMyIdentity({
        tagline: raw.tagline || null,
        description: raw.description || null,
        instagramUrl: raw.instagramUrl || null,
        whatsappNumber: raw.whatsappNumber || null,
        cancellationWindowHours: raw.cancellationWindowHours,
      })
      .subscribe({
        next: () => {
          this.identitySaving.set(false);
          this.showToast('Identidad actualizada.');
        },
        error: () => {
          this.identitySaving.set(false);
          this.showToast('No pudimos guardar los cambios. Intenta nuevamente.', 'danger');
        },
      });
  }

  protected async onLogoFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    if (!ACCEPTED_LOGO_TYPES.includes(file.type)) {
      this.showToast('Formato no soportado. Usa PNG, JPG, WEBP o SVG.', 'danger');
      return;
    }
    this.logoUploading.set(true);
    try {
      const logo = file.type === 'image/svg+xml' ? await file.text() : await this.resizeImageFile(file);
      await this.applyLogo(logo);
    } catch {
      this.showToast('No pudimos leer esa imagen. Intenta con otra.', 'danger');
    } finally {
      this.logoUploading.set(false);
    }
  }

  private resizeImageFile(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('file read error'));
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('image decode error'));
        img.onload = () => {
          const scale = Math.min(MAX_LOGO_DIMENSION / img.width, MAX_LOGO_DIMENSION / img.height, 1);
          const width = Math.max(1, Math.round(img.width * scale));
          const height = Math.max(1, Math.round(img.height * scale));
          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            reject(new Error('canvas not supported'));
            return;
          }
          ctx.clearRect(0, 0, width, height);
          ctx.drawImage(img, 0, 0, width, height);
          resolve(canvas.toDataURL('image/png'));
        };
        img.src = reader.result as string;
      };
      reader.readAsDataURL(file);
    });
  }

  private async applyLogo(logo: string): Promise<void> {
    const previous = this.logoSvg();
    this.logoSvg.set(logo);
    this.gymService.updateMyLogo(logo).subscribe({
      next: () => this.showToast('Logo actualizado.'),
      error: () => {
        this.logoSvg.set(previous);
        this.showToast('No pudimos guardar el logo. Intenta con otra imagen.', 'danger');
      },
    });
  }

  protected async onPhotoFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    if (this.photos().length >= this.maxPhotos) {
      this.showToast(`Ya tienes el máximo de ${this.maxPhotos} fotos.`, 'danger');
      return;
    }
    if (!ACCEPTED_PHOTO_TYPES.includes(file.type)) {
      this.showToast('Formato no soportado. Usa PNG, JPG o WEBP.', 'danger');
      return;
    }
    this.photoUploading.set(true);
    try {
      const data = await this.resizePhotoFile(file);
      const payload: CreateGymPhotoRequest = { data, caption: null };
      const photo = await firstValueFrom(this.gymService.createMyPhoto(payload));
      this.photos.update((list) => [...list, photo]);
    } catch {
      this.showToast('No pudimos subir esa foto. Intenta con otra.', 'danger');
    } finally {
      this.photoUploading.set(false);
    }
  }

  protected async removePhoto(photo: GymPhoto): Promise<void> {
    const confirmed = await this.confirmAction('Eliminar foto', '¿Eliminar esta foto de tus instalaciones?');
    if (!confirmed) {
      return;
    }
    this.gymService.deleteMyPhoto(photo.id).subscribe({
      next: () => this.photos.update((list) => list.filter((p) => p.id !== photo.id)),
      error: () => this.showToast('No pudimos eliminar esa foto.', 'danger'),
    });
  }

  private resizePhotoFile(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('file read error'));
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('image decode error'));
        img.onload = () => {
          const scale = Math.min(MAX_PHOTO_DIMENSION / img.width, MAX_PHOTO_DIMENSION / img.height, 1);
          const width = Math.max(1, Math.round(img.width * scale));
          const height = Math.max(1, Math.round(img.height * scale));
          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            reject(new Error('canvas not supported'));
            return;
          }
          ctx.drawImage(img, 0, 0, width, height);
          resolve(canvas.toDataURL('image/jpeg', 0.82));
        };
        img.src = reader.result as string;
      };
      reader.readAsDataURL(file);
    });
  }

  protected openAddBlock(): void {
    this.editingBlock.set(null);
    this.isModalOpen.set(true);
  }

  protected openEditBlock(block: GymBlock): void {
    this.editingBlock.set(block);
    this.isModalOpen.set(true);
  }

  protected closeModal(): void {
    this.isModalOpen.set(false);
  }

  protected openSeriesModal(): void {
    this.isSeriesModalOpen.set(true);
  }

  protected closeSeriesModal(): void {
    this.isSeriesModalOpen.set(false);
  }

  protected async confirmSeries(drafts: CreateGymBlockRequest[]): Promise<void> {
    this.isSeriesModalOpen.set(false);
    this.seriesCreating.set(true);
    let created = 0;
    let failed = 0;
    for (const draft of drafts) {
      try {
        await firstValueFrom(this.gymService.createMyBlock(draft));
        created++;
      } catch {
        failed++;
      }
    }
    this.seriesCreating.set(false);
    this.loadBlocks();
    this.showToast(
      failed === 0 ? `Se crearon ${created} bloques.` : `Se crearon ${created} bloques. ${failed} fallaron.`,
      failed === 0 ? 'success' : 'danger',
    );
  }

  protected saveBlock(payload: CreateGymBlockRequest | UpdateGymBlockRequest): void {
    const editing = this.editingBlock();
    const request = editing
      ? this.gymService.updateMyBlock(editing.id, payload as UpdateGymBlockRequest)
      : this.gymService.createMyBlock(payload as CreateGymBlockRequest);

    this.status.set('saving');
    request.subscribe({
      next: () => {
        this.status.set('idle');
        this.isModalOpen.set(false);
        this.loadBlocks();
      },
      error: () => this.status.set('error'),
    });
  }

  protected async removeBlock(block: GymBlock): Promise<void> {
    const confirmed = await this.confirmAction(
      'Eliminar bloque',
      `¿Eliminar "${block.label}"? Esta acción no se puede deshacer.`,
    );
    if (!confirmed) {
      return;
    }
    this.gymService.deleteMyBlock(block.id).subscribe({
      next: () => this.loadBlocks(),
      error: () => this.status.set('error'),
    });
  }

  protected openAddPlan(): void {
    this.editingPlan.set(null);
    this.isPlanModalOpen.set(true);
  }

  protected openEditPlan(plan: GymPlan): void {
    this.editingPlan.set(plan);
    this.isPlanModalOpen.set(true);
  }

  protected closePlanModal(): void {
    this.isPlanModalOpen.set(false);
  }

  protected savePlan(payload: CreateGymPlanRequest | UpdateGymPlanRequest): void {
    const editing = this.editingPlan();
    const request = editing
      ? this.gymService.updateMyPlan(editing.id, payload as UpdateGymPlanRequest)
      : this.gymService.createMyPlan(payload as CreateGymPlanRequest);

    this.status.set('saving');
    request.subscribe({
      next: () => {
        this.status.set('idle');
        this.isPlanModalOpen.set(false);
        this.loadPlans();
      },
      error: () => this.status.set('error'),
    });
  }

  protected async removePlan(plan: GymPlan): Promise<void> {
    const confirmed = await this.confirmAction('Eliminar plan', `¿Eliminar "${plan.name}"? Esta acción no se puede deshacer.`);
    if (!confirmed) {
      return;
    }
    this.gymService.deleteMyPlan(plan.id).subscribe({
      next: () => this.loadPlans(),
      error: () => this.status.set('error'),
    });
  }

  private async confirmAction(header: string, message: string, confirmText = 'Eliminar'): Promise<boolean> {
    const alert = await this.alertController.create({
      header,
      message,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        { text: confirmText, role: 'destructive' },
      ],
    });
    await alert.present();
    const { role } = await alert.onDidDismiss();
    return role === 'destructive';
  }

  protected submitMember(): void {
    if (this.memberForm.invalid) {
      return;
    }
    this.status.set('saving');
    this.memberService.create(this.memberForm.getRawValue()).subscribe({
      next: (member) => {
        this.memberForm.reset({ name: '', email: '' });
        this.status.set('idle');
        this.loadMembers();
        this.showToast(`Socio agregado: ${member.name}. Le enviamos un correo para activar su cuenta y elegir un plan.`);
      },
      error: () => this.status.set('error'),
    });
  }

  protected statusLabel(status: MembershipStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'Activo';
      case 'EXPIRING_SOON':
        return 'Por vencer';
      case 'EXPIRED':
        return 'Vencido';
      default:
        return 'Sin pago';
    }
  }

  protected statusColor(status: MembershipStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'EXPIRING_SOON':
        return 'warning';
      case 'EXPIRED':
        return 'danger';
      default:
        return 'medium';
    }
  }

  // Mismo patrón que togglingAdminId — id del socio con una acción de plan en curso, para
  // deshabilitar y mostrar spinner solo en ese botón mientras se espera la respuesta.
  protected readonly markingPaidId = signal<number | null>(null);
  protected readonly revokingPlanId = signal<number | null>(null);

  // Registro manual para dinero que no pasó por Flow.cl (efectivo/transferencia) — el admin
  // elige el plan que el socio pagó y queda persistido de verdad (GymService.simulatePlanPayment).
  // Reemplaza el ion-alert de radios planas: el admin necesita ver el mismo detalle de precio/
  // cupo (las "calugas") que el socio ve antes de pagar en /member — un ion-alert no admite
  // HTML/ion-badge dentro de sus inputs, así que se usa un ion-modal con app-mark-paid-modal.
  protected readonly markPaidMember = signal<Member | null>(null);

  protected markMemberPaid(member: Member): void {
    if (this.plans().length === 0) {
      this.showToast('Primero crea un plan en la pestaña Planes.', 'danger');
      return;
    }
    this.markPaidMember.set(member);
  }

  protected closeMarkPaidModal(): void {
    this.markPaidMember.set(null);
  }

  protected confirmMarkPaid(planId: number): void {
    const member = this.markPaidMember();
    if (!member) {
      return;
    }
    this.markingPaidId.set(member.id);
    this.memberService.markPaid(member.id, { planId }).subscribe({
      next: () => {
        this.markingPaidId.set(null);
        this.markPaidMember.set(null);
        this.showToast(`Pago registrado para ${member.name}.`);
        this.loadMembers();
      },
      error: () => {
        this.markingPaidId.set(null);
        this.showToast('No pudimos registrar el pago. Intenta nuevamente.', 'danger');
      },
    });
  }

  // Contraparte de markMemberPaid — para corregir un pago mal confirmado (ej. Flow lo marcó
  // aprobado pero en realidad falló) o dar de baja a un socio por cualquier otro motivo.
  protected async revokeMemberPlan(member: Member): Promise<void> {
    const confirmed = await this.confirmAction(
      'Quitar plan',
      `¿Quitarle el plan a ${member.name}? Va a dejar de poder reservar clases hasta que pague de nuevo.`,
      'Quitar plan',
    );
    if (!confirmed) {
      return;
    }
    this.revokingPlanId.set(member.id);
    this.memberService.revokePlan(member.id).subscribe({
      next: () => {
        this.revokingPlanId.set(null);
        this.showToast(`Se le quitó el plan a ${member.name}.`);
        this.loadMembers();
      },
      error: () => {
        this.revokingPlanId.set(null);
        this.showToast('No pudimos quitar el plan. Intenta nuevamente.', 'danger');
      },
    });
  }

  private async showToast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({
      message,
      duration: 4000,
      position: 'bottom',
      color,
    });
    await toast.present();
  }

  // Al salir, un admin de gimnasio vuelve a la página propia de SU gimnasio
  // (no al /login genérico de mygym) — más contextual, y esa página ya sabe
  // re-loguearlo si vuelve a tocar "Continuar con Google" (el backend de
  // /join detecta que el email ya existe y solo lo loguea, sin re-provisionar).
  protected async logout(): Promise<void> {
    await this.authService.logout();
    const slug = this.gymSlug();
    this.router.navigate([slug ? `/j/${slug}` : '/login']);
  }

  private loadGym(): void {
    this.gymService.getMine().subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.gymName.set(gym.name);
        this.gymSlug.set(gym.slug);
        this.logoSvg.set(gym.logoSvg);
        if (gym.themeColor) {
          this.themeColor.set(gym.themeColor);
        }
        this.themeMode.set(gym.themeMode ?? 'DARK');
        this.identityForm.patchValue({
          tagline: gym.tagline ?? '',
          description: gym.description ?? '',
          instagramUrl: gym.instagramUrl ?? '',
          whatsappNumber: gym.whatsappNumber ?? '',
          cancellationWindowHours: gym.cancellationWindowHours,
        });
        this.gymLoaded.set(true);
      },
      error: () => {
        this.status.set('error');
        this.gymLoaded.set(true);
      },
    });
  }

  private loadPhotos(): void {
    this.gymService.listMyPhotos().subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => this.status.set('error'),
    });
  }

  private loadBlocks(): void {
    this.gymService.listMyBlocks().subscribe({
      next: (blocks) => this.blocks.set(sortBlocksBySchedule(blocks)),
      error: () => this.status.set('error'),
    });
  }

  private loadPlans(): void {
    this.gymService.listMyPlans().subscribe({
      next: (plans) => this.plans.set(plans),
      error: () => this.status.set('error'),
    });
  }

  private loadMembers(): void {
    this.memberService.list().subscribe({
      next: (members) => this.members.set(members),
      error: () => this.status.set('error'),
    });
  }
}
