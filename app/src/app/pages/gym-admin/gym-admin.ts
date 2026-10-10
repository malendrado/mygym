import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
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
  IonListHeader,
  IonModal,
  IonNote,
  IonSearchbar,
  IonSegment,
  IonSegmentButton,
  IonSelect,
  IonSelectOption,
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
  banOutline,
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
  copyOutline,
  createOutline,
  ellipseOutline,
  eyeOutline,
  helpCircleOutline,
  hourglassOutline,
  imagesOutline,
  linkOutline,
  logOutOutline,
  logoGoogle,
  logoWhatsapp,
  megaphoneOutline,
  paperPlaneOutline,
  peopleOutline,
  personAddOutline,
  personCircleOutline,
  personOutline,
  playOutline,
  pricetagOutline,
  refreshOutline,
  removeCircleOutline,
  rocketOutline,
  searchOutline,
  settingsOutline,
  shieldCheckmarkOutline,
  timeOutline,
  trashOutline,
  tvOutline,
} from 'ionicons/icons';
import { BookingRulesCard } from '../../core/components/booking-rules-card/booking-rules-card';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { Gym } from '../../core/models/gym.model';
import { MemberService } from '../../core/services/member.service';
import { MemberListState } from '../../core/state/member-list.state';
import { PagedListState } from '../../core/state/paged-list.state';
import { PaginationBar } from '../../core/components/pagination-bar/pagination-bar';
import { TvScreenService } from '../../core/services/tv-screen.service';
import { TvScreen } from '../../core/models/tv-screen.model';
import {
  Admin,
  BANK_ACCOUNT_TYPES,
  BankTransferUpdateRequest,
  BookingRulesRequest,
  BlockOccurrenceAttendees,
  CHILE_BANKS,
  CreateAdminRequest,
  CreateGymBlockRequest,
  CreateGymPhotoRequest,
  CreateGymPlanRequest,
  DayOfWeek,
  FlowAccount,
  FlowAccountDetailsUpdateRequest,
  GymBlock,
  GymClosure,
  GymClosureCreateRequest,
  GymClosurePreview,
  GymClosureUpdateRequest,
  GymPhoto,
  GymPlan,
  UpdateGymBlockRequest,
  UpdateGymPlanRequest,
  sortBlocksBySchedule,
} from '../../core/models/gym.model';
import { Attendee, InviteStatus, Member, MembershipStatus } from '../../core/models/member.model';
import { CreateProfesorRequest, Profesor } from '../../core/models/profesor.model';
import { CreateWorkoutPlanRequest, MemberWorkoutLog, WorkoutPlan } from '../../core/models/workout.model';
import { ProfesorService } from '../../core/services/profesor.service';
import { WorkoutService } from '../../core/services/workout.service';
import { BloqueFormModal, DAYS } from '../admin/gyms/bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../admin/gyms/bloque-series-modal/bloque-series-modal';
import { PlanFormModal } from '../admin/gyms/plan-form-modal/plan-form-modal';
import { MarkPaidModal } from '../admin/gyms/mark-paid-modal/mark-paid-modal';
import { ClosureModal } from '../admin/gyms/closure-modal/closure-modal';
import { EditClosureModal } from '../admin/gyms/edit-closure-modal/edit-closure-modal';
import { ImportMembersModal } from '../admin/gyms/import-members-modal/import-members-modal';
import { WorkoutPlanModal } from '../admin/gyms/workout-plan-modal/workout-plan-modal';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../core/utils/class-category';
import { formatRut, rutFormatValidator } from '../../core/utils/rut';
import { toLogoImgSrc } from '../../core/utils/logo-src';
import { TourOverlay } from '../../core/components/tour-overlay/tour-overlay';
import { SectionTour, TourService } from '../../core/services/tour.service';
import { TourSeenService } from '../../core/services/tour-seen.service';

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
  'eye-outline': eyeOutline,
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
  'shield-checkmark-outline': shieldCheckmarkOutline,
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
  'rocket-outline': rocketOutline,
  'ellipse-outline': ellipseOutline,
  'link-outline': linkOutline,
  'copy-outline': copyOutline,
  'logo-google': logoGoogle,
  'barbell-outline': barbellOutline,
  'tv-outline': tvOutline,
  'ban-outline': banOutline,
  'play-outline': playOutline,
  'logo-whatsapp': logoWhatsapp,
});

type Status = 'idle' | 'loading' | 'saving';
type Section = 'general' | 'blocks' | 'plans' | 'members' | 'branding' | 'screens' | 'history' | 'closures';

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

// "HH:mm" en el huso del gym — mismo criterio que todayIsoDate(), para precargar el filtro de
// franja horaria con la que corresponde a AHORA (ver selectedTimeBand más abajo).
function nowTimeString(): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Santiago',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date());
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
  branding: 'Mi marca',
  screens: 'Pantallas',
  history: 'Historial',
  closures: 'Cierres',
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
    IonListHeader,
    IonBadge,
    IonChip,
    IonModal,
    IonNote,
    IonSearchbar,
    IonSegment,
    IonSegmentButton,
    IonSelect,
    IonSelectOption,
    IonText,
    IonTextarea,
    BloqueFormModal,
    BloqueSeriesModal,
    PlanFormModal,
    MarkPaidModal,
    ClosureModal,
    EditClosureModal,
    ImportMembersModal,
    PaginationBar,
    WorkoutPlanModal,
    BookingRulesCard,
    IonSpinner,
    TourOverlay,
  ],
  templateUrl: './gym-admin.html',
  styleUrl: './gym-admin.scss',
})
export class GymAdmin implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly memberService = inject(MemberService);
  private readonly tvScreenService = inject(TvScreenService);
  private readonly profesorService = inject(ProfesorService);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);
  private readonly tourService = inject(TourService);
  protected readonly tourSeen = inject(TourSeenService);

  protected readonly status = signal<Status>('idle');
  protected readonly section = signal<Section>('general');
  protected readonly sectionLabel = computed(() => SECTION_LABELS[this.section()]);
  protected readonly adminFirstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'admin');
  // Acceso de solo-lectura a la demo comercial (ver Role.DEMO_ADMIN / SecurityConfig en el
  // backend) — el bloqueo real de escritura ya está en el backend, esto solo maneja el toggle
  // "ver como socio" y el difuminado de la sección de Marca (ver template).
  protected readonly isDemoAdmin = computed(() => this.authService.currentUser()?.role === 'DEMO_ADMIN');
  // "Memoria Viva": un PROFESOR entra al mismo panel de gym-admin (ver roleGuard en web.routes.ts)
  // pero solo puede escribir en rutina/bitácora (ver SecurityConfig en el backend) — nunca ve la
  // gestión de profesores (eso es exclusivo de GYM_ADMIN, ver template).
  protected readonly isProfesor = computed(() => this.authService.currentUser()?.role === 'PROFESOR');
  protected readonly tip = ADMIN_TIPS[Math.floor(Math.random() * ADMIN_TIPS.length)];
  protected readonly gym = signal<Gym | null>(null);
  protected readonly gymName = signal('');
  protected readonly gymSlug = signal<string | null>(null);
  protected readonly gymLoaded = signal(false);

  // Checklist de "Próximos pasos" en General — pedido explícito del usuario tras ver que la
  // pestaña quedaba casi vacía debajo de las calugas de socios ("no muestra casi nada").
  // Consultada la skill ui-ux-pro-max: nunca dejar un dashboard con espacio muerto, mostrar
  // acción concreta en vez de relleno decorativo. Cada item apunta a la pestaña donde se
  // resuelve; la lista entera desaparece sola apenas los 4 items están completos (no queda
  // como "tutorial" pegado para siempre).
  protected readonly hasActivePlan = computed(() => this.plans().some((p) => p.active));
  protected readonly hasSchedule = computed(() => this.blocks().length > 0);
  protected readonly hasBranding = computed(() => !!this.gym()?.logoSvg);
  protected readonly hasBankTransfer = computed(() => {
    const g = this.gym();
    return !!(g?.bankName && g.bankAccountType && g.bankAccountNumber && g.bankHolderRut && g.bankHolderName);
  });
  protected readonly hasFlowAccountDetails = computed(() => {
    const a = this.flowAccount();
    return !!(a?.companyRut && a.companyName && a.legalRepName && a.legalRepRut && a.contactEmail);
  });
  protected readonly setupChecklist = computed(() => [
    { key: 'plans', label: 'Crea al menos un plan de membresía', done: this.hasActivePlan(), section: 'plans' as Section },
    { key: 'schedule', label: 'Configura tus horarios de clases', done: this.hasSchedule(), section: 'blocks' as Section },
    { key: 'branding', label: 'Sube el logo de tu gimnasio', done: this.hasBranding(), section: 'branding' as Section },
    {
      key: 'bank',
      label: 'Carga tus datos bancarios (transferencia)',
      done: this.hasBankTransfer(),
      section: 'general' as Section,
    },
    {
      key: 'flow',
      label: 'Completa los datos de tu cuenta Pago Online',
      done: this.hasFlowAccountDetails(),
      section: 'general' as Section,
    },
  ]);
  protected readonly setupPending = computed(() => this.setupChecklist().filter((item) => !item.done));

  protected readonly blocks = signal<GymBlock[]>([]);
  // Antes se mostraban TODOS los bloques en una sola lista larga — con varios
  // días configurados, el scroll se volvía interminable (reportado por el
  // usuario). `null` = "Todos los días". Precargado con el día/franja de
  // AHORA (pedido explícito): lo primero que el admin quiere ver al entrar
  // es lo que está pasando en este momento, no la semana completa — si
  // quiere ver otra cosa, usa los chips ("Todos"/otro día) o el buscador.
  protected readonly selectedDay = signal<DayOfWeek | null>(dayOfWeekOfDate(todayIsoDate()));
  protected readonly days = DAYS;
  protected readonly selectedTimeBand = signal<TimeBand | null>(timeBandOf(nowTimeString()));
  protected readonly timeBandOptions = TIME_BAND_OPTIONS;
  protected readonly timeBandAllHint = TIME_BAND_ALL_HINT;
  // Client-side, igual criterio que memberSearchQuery — los bloques del gym ya viven completos
  // en memoria (blocks()), así que buscar por texto no necesita pedirle nada al backend.
  protected readonly blockSearchQuery = signal('');
  protected readonly filteredBlocks = computed(() => {
    const day = this.selectedDay();
    const band = this.selectedTimeBand();
    const query = this.blockSearchQuery().trim().toLowerCase();
    return this.blocks().filter(
      (b) =>
        (!day || b.dayOfWeek === day) &&
        (!band || timeBandOf(b.startTime) === band) &&
        (!query ||
          b.label.toLowerCase().includes(query) ||
          (b.category ?? '').toLowerCase().includes(query) ||
          (b.instructorName ?? '').toLowerCase().includes(query)),
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
  // El servidor topa a 25 socios por búsqueda — true = hay más coincidencias de las mostradas.
  protected readonly reservationSearchTruncated = signal(false);
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
  // Lista paginada de socios — búsqueda, filtros y conteos de las calugas viven en el servidor
  // (ver MemberListState). `members` es solo la página visible; las calugas leen los conteos del
  // gym completo, no cuentan filas del navegador.
  protected readonly memberList = new MemberListState(
    (query) => this.memberService.listPage(query),
    () => this.memberService.summary(),
  );
  protected readonly members = this.memberList.members;
  // Emails de TODOS los socios, pedidos al abrir el modal de importación (ver openImportModal).
  protected readonly memberEmails = signal<string[]>([]);
  protected readonly isImportModalOpen = signal(false);
  protected readonly activeMembers = this.memberList.active;
  protected readonly expiringSoonMembers = this.memberList.expiringSoon;
  protected readonly expiredMembers = this.memberList.expired;
  protected readonly unpaidMembers = this.memberList.unpaid;
  // Usados solo para targetear un botón de UNA fila puntual en el tour guiado — el spotlight
  // necesita un selector único, no una clase repetida por cada socio de la lista.
  protected readonly firstUnpaidMemberId = computed(() => this.members().find((m) => !m.planId)?.id ?? null);
  protected readonly firstMemberWithPlanId = computed(() => this.members().find((m) => !!m.planId)?.id ?? null);
  protected readonly firstMemberId = computed(() => this.members()[0]?.id ?? null);
  protected readonly memberStatusFilter = this.memberList.statusFilter;
  // Antes memberStatusFilter/memberInviteFilter eran dos ejes independientes que se
  // combinaban con AND (podían dar 0 socios sin que se viera obvio por qué) — las 6 calugas
  // se ven como un único grupo de filtro, así que ahora click en cualquiera reemplaza
  // cualquier filtro activo del otro eje, nunca se suman. Ver viewMembersByStatus/ByInvite.
  // "Invitados registrados" (contador/filtro propio) se retiró a pedido explícito del usuario:
  // alguien invitado que ya se registró pero no pagó ya cuenta como "Sin pago" (eje de pago,
  // independiente) — tener un tercer bucket separado para lo mismo era ruido. El badge por
  // fila (inviteStatusLabel) se mantiene SIEMPRE, incluso ya activo — es información histórica
  // ("este socio lo invité yo"), no un estado que compita con el de pago.
  protected readonly invitedPendingMembers = this.memberList.invitedPending;
  protected readonly memberInviteFilter = this.memberList.inviteFilter;
  // El buscador es del servidor (ver MemberListState.setSearch, con debounce): la lista ya no
  // está completa en memoria, así que filtrar acá solo vería la página visible.
  protected readonly memberSearchQuery = this.memberList.searchQuery;
  protected readonly filteredMembers = this.memberList.members;
  protected readonly isModalOpen = signal(false);
  protected readonly editingBlock = signal<GymBlock | null>(null);
  protected readonly isSeriesModalOpen = signal(false);
  protected readonly seriesCreating = signal(false);
  protected readonly isPlanModalOpen = signal(false);
  protected readonly editingPlan = signal<GymPlan | null>(null);

  protected readonly logoSvg = signal<string | null>(null);
  protected readonly logoSrc = computed(() => toLogoImgSrc(this.logoSvg()));
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

  protected readonly tvScreens = signal<TvScreen[]>([]);
  protected readonly claimingScreen = signal(false);
  protected readonly removingScreenId = signal<number | null>(null);
  protected readonly tvScreenError = signal<string | null>(null);
  protected readonly tvScreenForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(6)] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
  });

  // Cierre de emergencia (ver GymClosureService) — pestaña Cierres.
  // Historial de cierres paginado (10 por vez, "Cargar más") — crece con cada emergencia.
  protected readonly closureList = new PagedListState<GymClosure>(
    (query) => this.gymService.listMyClosuresPage(query.page, query.size),
    10,
    'append',
  );
  protected readonly closures = this.closureList.items;
  protected readonly isClosureModalOpen = signal(false);
  protected readonly closurePreview = signal<GymClosurePreview | null>(null);
  protected readonly closurePreviewing = signal(false);
  protected readonly closurePreviewError = signal<string | null>(null);
  protected readonly closureSaving = signal(false);
  protected readonly liftingClosureId = signal<number | null>(null);
  protected readonly editingClosure = signal<GymClosure | null>(null);
  protected readonly editClosurePreview = signal<GymClosurePreview | null>(null);
  protected readonly editClosurePreviewing = signal(false);
  protected readonly editClosurePreviewError = signal<string | null>(null);
  protected readonly editClosureSaving = signal(false);

  protected readonly identityForm = new FormGroup({
    tagline: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(160)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(600)] }),
    instagramUrl: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    whatsappNumber: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
  });
  protected readonly identitySaving = signal(false);

  // Pestaña Horarios → tarjeta "Reglas de reserva" (componente compartido con el super-admin).
  protected readonly bookingRules = signal<BookingRulesRequest | null>(null);
  protected readonly bookingRulesSaving = signal(false);

  protected readonly chileBanks = CHILE_BANKS;
  protected readonly bankAccountTypes = BANK_ACCOUNT_TYPES;
  protected readonly bankTransferForm = new FormGroup({
    bankName: new FormControl('', { nonNullable: true }),
    bankNameOther: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    accountType: new FormControl('', { nonNullable: true }),
    accountNumber: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
    holderRut: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20), rutFormatValidator()] }),
    holderName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    confirmationEmail: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(160)] }),
  });
  protected readonly bankTransferSaving = signal(false);

  // Cuenta Pago Online (Flow.cl) — el dueño del gym completa empresa/representante
  // legal/contacto; la API key/secret key las carga el super-admin aparte, una vez que
  // estos datos ya estén completos (ver GymAdminController.updateMyFlowAccount).
  protected readonly flowAccount = signal<FlowAccount | null>(null);
  protected readonly flowAccountSaving = signal(false);
  protected readonly flowAccountForm = new FormGroup({
    companyRut: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20), rutFormatValidator()] }),
    companyName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(160)] }),
    businessActivity: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(160)] }),
    companyAddress: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    vatCondition: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    legalRepName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    legalRepRut: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20), rutFormatValidator()] }),
    legalRepPhone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    contactEmail: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(160)] }),
    contactName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    contactPhone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
  });

  protected readonly photos = signal<GymPhoto[]>([]);
  protected readonly photoUploading = signal(false);
  protected readonly maxPhotos = MAX_PHOTOS;

  constructor() {
    this.loadGym();
    this.loadBlocks();
    this.loadPlans();
    this.loadMembers();
    this.loadPhotos();
    this.loadTvScreens();
    this.loadClosures();
    if (!this.isProfesor()) {
      this.loadProfesores();
      this.loadAdmins();
    }

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

    // "Todavía no se conectó" quedaba pegado en la UI aunque la TV ya estuviera sondeando de
    // verdad (lastPolledAt SÍ se actualiza en el backend en cada poll) — loadTvScreens() solo se
    // llamaba una vez al entrar, nunca se refrescaba. Mientras la pestaña "Pantallas" está
    // abierta, refresca cada 15s (la TV sondea cada 25s, ver tv-screen.ts) para que el estado se
    // ponga al día solo, sin que el admin tenga que recargar la página entera.
    effect(() => {
      if (this.section() === 'screens') {
        this.loadTvScreens();
        this.tvScreensPollTimer = setInterval(() => this.loadTvScreens(), 15_000);
      } else if (this.tvScreensPollTimer) {
        clearInterval(this.tvScreensPollTimer);
        this.tvScreensPollTimer = null;
      }
    });
  }

  private tvScreensPollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnDestroy(): void {
    const root = document.documentElement.style;
    for (const property of THEMED_ROOT_PROPERTIES) {
      root.removeProperty(property);
    }
    clearThemeOverrides();
    if (this.tvScreensPollTimer) {
      clearInterval(this.tvScreensPollTimer);
    }
  }

  protected setSection(section: Section): void {
    this.section.set(section);
    if (section === 'history') {
      this.loadHistoryAttendees();
    }
  }

  // Mismo número que WHATSAPP_NUMBER en landing.ts — duplicado a propósito, no vale la pena
  // compartir una constante de 10 dígitos entre dos páginas que nunca se importan entre sí.
  protected readonly demoWhatsappUrl = `https://wa.me/56964641042?text=${encodeURIComponent(
    'Hola! Vi la demo de mygym y quiero esto para mi gimnasio.',
  )}`;

  // Tour guiado por sección — antes era un único recorrido de 11 pasos que saltaba de pestaña
  // en pestaña (beforeShow + setSection); el usuario pidió partirlo: cada pestaña tiene su
  // propio mini-tour acotado a lo que ya se ve ahí, sin mover al prospecto de donde está parado.
  // "crear plan" y "crear clase" siguen siendo obligatorios (pedido explícito), el resto recorre
  // lo que más diferencia a mygym de la competencia (ver memoria mygym_competencia_boxpro). El
  // código de cada tour (ej. 'A_PLANS') es el que se guarda en demo_tour_progress — ver
  // DemoTourService.ALLOWED_TOURS en el backend, debe coincidir exactamente.
  private readonly sectionTours: Partial<Record<Section, SectionTour>> = {
    plans: {
      tour: 'A_PLANS',
      label: 'Planes',
      steps: [
        {
          title: 'Crea tus planes de cobro',
          body: 'Define precio y cupo mensual de cada plan — tus socios eligen uno de estos al pagar.',
          targetSelector: '[data-tour="plans-add"]',
        },
        {
          title: 'Edita o pausa un plan sin borrarlo',
          body: 'Cambia precio o cupo cuando quieras, o desactívalo para que no se siga ofreciendo sin perder a los socios que ya lo tienen.',
          targetSelector: '.plan-card__header',
        },
      ],
    },
    blocks: {
      tour: 'A_BLOCKS',
      label: 'Horarios',
      steps: [
        {
          title: 'Crea tus clases',
          body: 'Cada bloque es una clase recurrente: día, horario y cupo.',
          targetSelector: '[data-tour="blocks-add"]',
          // Por defecto la pestaña precarga el filtro de HOY/AHORA — si al gimnasio demo real
          // le toca un día/franja sin bloques, los pasos 4 y 5 (editar, ver quién reservó) no
          // tienen nada que iluminar. El tour necesita ver TODOS los bloques, no el recorte del
          // momento real en que alguien lo esté mirando.
          beforeShow: () => {
            this.selectedDay.set(null);
            this.selectedTimeBand.set(null);
          },
        },
        {
          title: 'Arma tu grilla semanal de una sola vez',
          body: '"Generar bloques" crea una serie completa (ej. Spinning lunes-miércoles-viernes 7am) en un solo paso, no clase por clase.',
          targetSelector: '[data-tour="blocks-generate"]',
        },
        {
          title: 'Encuentra cualquier clase al instante',
          body: 'Busca por nombre, categoría o instructor, o filtra por día y franja horaria — útil cuando tienes decenas de clases.',
          targetSelector: '[data-tour="blocks-search"]',
        },
        {
          title: 'Edita o elimina sin perder lo configurado',
          body: 'Cambia horario, cupo o instructor de una clase ya creada, o elimínala si ya no corre.',
          targetSelector: '.block-card__actions',
        },
        {
          title: 'Ve quién reservó cada clase',
          body: 'Lista de asistentes por bloque, con opción de liberar el cupo de alguien puntual si hace falta.',
          targetSelector: '.block-card__attendees-toggle',
        },
      ],
    },
    members: {
      tour: 'A_MEMBERS',
      label: 'Socios',
      steps: [
        {
          title: 'El estado de cada socio, de un vistazo',
          body: 'Activo, por vencer, vencido o sin pago — toca cualquiera para filtrar la lista al instante.',
          targetSelector: '[data-tour="members-stats"]',
        },
        {
          title: 'Agrega socios uno por uno...',
          body: 'O migra tu base completa de otro sistema de una sola vez con "Importar varios socios desde Excel" — ideal si ya tienes cientos de socios.',
          targetSelector: '[data-tour="members-add"]',
        },
        {
          title: 'Encuentra a cualquier socio al instante',
          body: 'Busca por nombre o email en toda tu lista, sin scrollear.',
          targetSelector: '[data-tour="members-search"]',
        },
        {
          title: 'Marca un pago en segundos',
          body: 'Transferencia, efectivo o Flow — confirmas el pago de un socio sin pago con un clic, y pasa a "Activo" al instante.',
          targetSelector: '[data-tour="members-mark-paid"]',
        },
        {
          title: 'Suma profesores a tu equipo',
          body: 'Dales acceso de solo-gestión: pueden ver reservas y marcar asistencia, sin tocar tus planes ni tu marca.',
          targetSelector: '[data-tour="profesor-add"]',
        },
        {
          title: 'Quita un plan si hace falta',
          body: 'Por impago, conflicto o cualquier motivo — el socio vuelve a "Sin pago" sin perder su cuenta.',
          targetSelector: '[data-tour="members-revoke-plan"]',
        },
        {
          title: 'Coaching real, no solo reservas',
          body: 'Abre la rutina de cualquier socio, ve qué anotó en su última clase y arma su plan de entrenamiento.',
          targetSelector: '[data-tour="members-workout"]',
        },
        {
          title: 'Gestiona bajas reales',
          body: 'Elimina a un socio permanentemente cuando deja el gimnasio — no solo altas, control completo del ciclo de vida.',
          targetSelector: '[data-tour="members-delete"]',
        },
      ],
    },
    general: {
      tour: 'A_GENERAL',
      label: 'General',
      steps: [
        {
          title: 'Cobra con tarjeta, directo a tu cuenta',
          body: 'Completa estos datos y mygym activa Flow en tu propia cuenta — tus socios pagan online y el dinero cae directo a ti, no a una cuenta compartida.',
          targetSelector: '[data-tour="flow-account"]',
        },
        {
          title: 'O por transferencia, sin comisión',
          body: 'Carga tus datos bancarios una sola vez — tus socios los ven al pagar, y tú confirmas el pago a mano cuando llega.',
          targetSelector: '[data-tour="bank-transfer"]',
        },
        {
          title: 'Invita a otro administrador',
          body: 'Si tienes un socio de negocio o un encargado, dale acceso completo a este mismo panel — entra con su propio Google.',
          targetSelector: '[data-tour="admin-add"]',
        },
      ],
    },
    branding: {
      tour: 'A_BRANDING',
      label: 'Mi marca',
      steps: [
        {
          title: 'Sube tu logo',
          body: 'Aparece en el panel de tus socios y en tu página de alta pública — marca blanca real, no "mygym con otro logo".',
          targetSelector: '[data-tour="branding-logo"]',
        },
        {
          title: 'Tu marca, no la nuestra',
          body: 'Tu color, tu logo — tus socios ven tu app, no una plantilla genérica.',
          targetSelector: '[data-tour="branding-color"]',
        },
        {
          title: 'Cuenta quién eres',
          body: 'Frase, descripción, Instagram y WhatsApp — alimenta tu página de alta pública y el perfil que ven tus socios.',
          targetSelector: '[data-tour="branding-identity"]',
        },
        {
          title: 'Muestra tus instalaciones',
          body: 'Hasta 8 fotos que se ven en tu página de alta y en el perfil de tus socios — un gimnasio con fotos reales se ve profesional desde el primer contacto.',
          targetSelector: '[data-tour="branding-photos"]',
        },
      ],
    },
    screens: {
      tour: 'A_SCREENS',
      label: 'Pantallas',
      steps: [
        {
          title: 'Pantalla de TV en la recepción',
          body: 'La clase de ahora y el check-in por QR, sin comprar ningún equipo nuevo.',
          targetSelector: '[data-tour="screens-tv"]',
        },
        {
          title: 'Vincula una TV en 30 segundos',
          body: 'Abre mygym.cl/tv en cualquier pantalla, aparece un código de 6 caracteres, lo ingresas acá con un nombre — sin cables ni apps que instalar. Puedes tener varias pantallas a la vez (recepción, sala).',
          targetSelector: '[data-tour="screens-pairing"]',
        },
      ],
    },
    closures: {
      tour: 'A_CLOSURES',
      label: 'Cierres',
      steps: [
        {
          title: 'Cierre de emergencia',
          body: 'Por fuerza mayor: cancela las clases afectadas y avisa a todos tus socios, con un clic.',
          targetSelector: '[data-tour="closures-open"]',
        },
        {
          title: 'Control total, incluso después de abrirlo',
          body: 'Ve el historial de cierres con cuántas reservas se cancelaron y a cuántos socios se avisó, edítalo si cambió algo, o levántalo antes de que termine si fue un error.',
          targetSelector: '[data-tour="closures-list"]',
        },
      ],
    },
    history: {
      tour: 'A_HISTORY',
      label: 'Historial',
      steps: [
        {
          title: 'Historial de asistencia',
          body: 'Revisa semana por semana quién vino a cada clase, no solo quién reservó.',
          targetSelector: '[data-tour="history"]',
        },
        {
          title: '"¿A qué clases va Juan?"',
          body: 'Busca por el nombre de un socio y ve todas sus reservas, sin recorrer semana por semana.',
          targetSelector: '[data-tour="history-search"]',
        },
        {
          title: 'Navega cualquier semana',
          body: 'Avanza o retrocede semana a semana, o salta directo a una fecha — útil para resolver un reclamo o auditar un período puntual.',
          targetSelector: '[data-tour="history-week-nav"]',
        },
      ],
    },
  };

  protected readonly currentSectionTour = computed(() => this.sectionTours[this.section()] ?? null);

  protected startSectionTour(): void {
    const sectionTour = this.currentSectionTour();
    if (!sectionTour) {
      return;
    }
    this.tourSeen.markAdminTourSeen();
    this.tourService.start(sectionTour.tour, sectionTour.steps);
  }

  protected viewMembersByStatus(status: MembershipStatus): void {
    this.memberList.setStatusFilter(status);
    this.section.set('members');
    this.scrollToMembersList();
  }

  protected clearMemberStatusFilter(): void {
    this.memberList.setStatusFilter(null);
  }

  protected viewMembersByInvite(status: Exclude<InviteStatus, null>): void {
    this.memberList.setInviteFilter(status);
    this.section.set('members');
    this.scrollToMembersList();
  }

  // Tocar una caluga de estado ya filtraba la lista y cambiaba a la pestaña Socios, pero la
  // tarjeta con la lista real queda más abajo (debajo de "Agregar socio" y "Profesores") — el
  // usuario tenía que scrollear a mano para ver el resultado del filtro que acababa de aplicar.
  // setTimeout(0) porque recién acá arranca el @else if de 'members' en el DOM (el cambio de
  // section() todavía no se renderizó en el mismo tick donde se llama esto).
  private scrollToMembersList(): void {
    setTimeout(() => {
      document.getElementById('members-list-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  protected clearMemberInviteFilter(): void {
    this.memberList.setInviteFilter(null);
  }

  protected onMemberPage(page: number): void {
    this.memberList.goTo(page);
    this.scrollToMembersList();
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
    const query = this.blockSearchQuery().trim();
    if (query) {
      return `No encontramos clases que coincidan con "${query}".`;
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
      this.reservationSearchTruncated.set(false);
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
      next: (response) => {
        this.reservationSearchResults.set(response.results);
        this.reservationSearchTruncated.set(response.truncated);
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
      error: () => this.handleWriteError(() => this.showToast('No pudimos cancelar la reserva. Intenta nuevamente.', 'danger')),
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
        this.handleWriteError(() => this.showToast('No pudimos guardar el color. Intenta nuevamente.', 'danger'));
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
      })
      .subscribe({
        next: () => {
          this.identitySaving.set(false);
          this.showToast('Identidad actualizada.');
        },
        error: () => {
          this.identitySaving.set(false);
          this.handleWriteError(() => this.showToast('No pudimos guardar los cambios. Intenta nuevamente.', 'danger'));
        },
      });
  }

  protected saveBookingRules(rules: BookingRulesRequest): void {
    this.bookingRulesSaving.set(true);
    this.gymService.updateMyBookingRules(rules).subscribe({
      next: () => {
        this.bookingRulesSaving.set(false);
        this.bookingRules.set(rules);
        this.showToast('Reglas de reserva guardadas.');
      },
      error: () => {
        this.bookingRulesSaving.set(false);
        this.handleWriteError(() => this.showToast('No pudimos guardar las reglas. Intenta nuevamente.', 'danger'));
      },
    });
  }

  protected saveBankTransfer(): void {
    if (this.bankTransferForm.invalid) {
      return;
    }
    const raw = this.bankTransferForm.getRawValue();
    const bankName = raw.bankName === 'Otro' ? raw.bankNameOther : raw.bankName;
    const payload: BankTransferUpdateRequest = {
      bankName: bankName || null,
      accountType: raw.accountType || null,
      accountNumber: raw.accountNumber || null,
      holderRut: raw.holderRut || null,
      holderName: raw.holderName || null,
      confirmationEmail: raw.confirmationEmail || null,
    };
    this.bankTransferSaving.set(true);
    this.gymService.updateMyBankTransfer(payload).subscribe({
      next: () => {
        this.bankTransferSaving.set(false);
        this.showToast('Datos bancarios actualizados.');
      },
      error: () => {
        this.bankTransferSaving.set(false);
        this.handleWriteError(() => this.showToast('No pudimos guardar los datos bancarios. Intenta nuevamente.', 'danger'));
      },
    });
  }

  // Formatea a "XX.XXX.XXX-D" al salir del campo — si lo que se escribió no es un RUT válido
  // (dígito verificador incorrecto), lo deja tal cual para que el mensaje de error se lea
  // junto al valor que el usuario realmente tipeó, no un formato a medio aplicar.
  protected formatRutOnBlur(control: FormControl<string>): void {
    control.setValue(formatRut(control.value));
  }

  protected saveFlowAccount(): void {
    if (this.flowAccountForm.invalid) {
      return;
    }
    const raw = this.flowAccountForm.getRawValue();
    const payload: FlowAccountDetailsUpdateRequest = {
      companyRut: raw.companyRut || null,
      companyName: raw.companyName || null,
      businessActivity: raw.businessActivity || null,
      companyAddress: raw.companyAddress || null,
      vatCondition: raw.vatCondition || null,
      legalRepName: raw.legalRepName || null,
      legalRepRut: raw.legalRepRut || null,
      legalRepPhone: raw.legalRepPhone || null,
      contactEmail: raw.contactEmail || null,
      contactName: raw.contactName || null,
      contactPhone: raw.contactPhone || null,
    };
    this.flowAccountSaving.set(true);
    this.gymService.updateMyFlowAccount(payload).subscribe({
      next: (account) => {
        this.flowAccountSaving.set(false);
        this.flowAccount.set(account);
        this.showToast('Datos de la cuenta Pago Online actualizados.');
      },
      error: () => {
        this.flowAccountSaving.set(false);
        this.handleWriteError(() =>
          this.showToast('No pudimos guardar la cuenta Pago Online. Intenta nuevamente.', 'danger'),
        );
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
        this.handleWriteError(() => this.showToast('No pudimos guardar el logo. Intenta con otra imagen.', 'danger'));
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
      this.handleWriteError(() => this.showToast('No pudimos subir esa foto. Intenta con otra.', 'danger'));
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
      error: () => this.handleWriteError(() => this.showToast('No pudimos eliminar esa foto.', 'danger')),
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
    // Mismo criterio que ImportMembersModal.confirmImport — un DEMO_ADMIN nunca puede crear
    // bloques, así que no tiene sentido intentar N requests que van a fallar todas igual.
    if (this.isDemoAdmin()) {
      this.showToast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    this.seriesCreating.set(true);
    // Un solo request con todos los bloques — antes era un POST secuencial por bloque (podían
    // ser ~80-300 según la configuración elegida), cada uno esperando al anterior.
    try {
      const results = await firstValueFrom(this.gymService.createMyBlocksBatch(drafts));
      const created = results.filter((r) => r.success).length;
      const failed = results.length - created;
      this.showToast(
        failed === 0 ? `Se crearon ${created} bloques.` : `Se crearon ${created} bloques. ${failed} fallaron.`,
        failed === 0 ? 'success' : 'danger',
      );
    } catch {
      this.showToast('No pudimos crear los bloques. Intenta nuevamente.', 'danger');
    }
    this.seriesCreating.set(false);
    this.loadBlocks();
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
      error: (err: Error) => {
        this.status.set('idle');
        this.handleWriteError(() =>
          this.showToast(err.message || 'No pudimos guardar el bloque. Intenta nuevamente.', 'danger'),
        );
      },
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
      error: (err: Error) =>
        this.handleWriteError(() => this.showToast(err.message || 'No pudimos eliminar el bloque.', 'danger')),
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
      error: (err: Error) => {
        this.status.set('idle');
        this.handleWriteError(() =>
          this.showToast(err.message || 'No pudimos guardar el plan. Intenta nuevamente.', 'danger'),
        );
      },
    });
  }

  protected async removePlan(plan: GymPlan): Promise<void> {
    const confirmed = await this.confirmAction('Eliminar plan', `¿Eliminar "${plan.name}"? Esta acción no se puede deshacer.`);
    if (!confirmed) {
      return;
    }
    this.gymService.deleteMyPlan(plan.id).subscribe({
      next: () => this.loadPlans(),
      error: (err: Error) =>
        this.handleWriteError(() => this.showToast(err.message || 'No pudimos eliminar el plan.', 'danger')),
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
      error: (err: Error) => {
        this.status.set('idle');
        this.handleWriteError(() =>
          this.showToast(err.message || 'No pudimos agregar al socio. Intenta nuevamente.', 'danger'),
        );
      },
    });
  }

  protected openImportModal(): void {
    this.isImportModalOpen.set(true);
    // La lista visible es solo una página: para detectar socios ya existentes el modal necesita
    // los emails de todo el gym. Llegan mucho antes de que el admin elija el archivo.
    this.memberService.emails().subscribe({ next: (emails) => this.memberEmails.set(emails), error: () => undefined });
  }

  protected closeImportModal(): void {
    this.isImportModalOpen.set(false);
  }

  protected onMembersImported(): void {
    this.loadMembers();
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
  protected readonly deletingMemberId = signal<number | null>(null);

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

  protected confirmMarkPaid({ planId, bank }: { planId: number; bank: string }): void {
    const member = this.markPaidMember();
    if (!member) {
      return;
    }
    this.markingPaidId.set(member.id);
    this.memberService.markPaid(member.id, { planId, bank }).subscribe({
      next: () => {
        this.markingPaidId.set(null);
        this.markPaidMember.set(null);
        this.showToast(`Pago registrado para ${member.name}.`);
        this.loadMembers();
      },
      error: () => {
        this.markingPaidId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos registrar el pago. Intenta nuevamente.', 'danger'));
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
        this.handleWriteError(() => this.showToast('No pudimos quitar el plan. Intenta nuevamente.', 'danger'));
      },
    });
  }

  // Contraparte de gym-form.ts (SUPER_ADMIN) — pedido explícito del usuario para que el propio
  // dueño del gimnasio también pueda borrar un socio, no solo alguien de mygym.
  protected async deleteMember(member: Member): Promise<void> {
    const confirmed = await this.confirmAction(
      'Eliminar socio permanentemente',
      `¿Eliminar a ${member.name} (${member.email})? Esto borra TODO su registro: reservas, historial de clases y pagos. No se puede deshacer.`,
      'Eliminar para siempre',
    );
    if (!confirmed) {
      return;
    }
    this.deletingMemberId.set(member.id);
    this.memberService.deleteMember(member.id).subscribe({
      next: () => {
        this.deletingMemberId.set(null);
        this.loadMembers();
        this.showToast(`${member.name} fue eliminado permanentemente.`);
      },
      error: () => {
        this.deletingMemberId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos eliminar al socio. Intenta nuevamente.', 'danger'));
      },
    });
  }

  // ---- "Memoria Viva": profesores (solo GYM_ADMIN los crea, ver template) ----

  protected readonly profesores = signal<Profesor[]>([]);
  protected readonly newProfesorName = signal('');
  protected readonly newProfesorEmail = signal('');
  protected readonly creatingProfesor = signal(false);
  protected readonly removingProfesorId = signal<number | null>(null);

  private loadProfesores(): void {
    this.profesorService.list().subscribe({ next: (list) => this.profesores.set(list) });
  }

  protected addProfesor(): void {
    const name = this.newProfesorName().trim();
    const email = this.newProfesorEmail().trim();
    if (!name || !email) {
      return;
    }
    this.creatingProfesor.set(true);
    this.profesorService.create({ name, email } as CreateProfesorRequest).subscribe({
      next: (profesor) => {
        this.creatingProfesor.set(false);
        this.profesores.update((list) => [...list, profesor]);
        this.newProfesorName.set('');
        this.newProfesorEmail.set('');
        this.showToast(`${profesor.name} ya puede entrar como profesor.`);
      },
      error: () => {
        this.creatingProfesor.set(false);
        this.handleWriteError(() => this.showToast('No pudimos agregar al profesor. Intenta nuevamente.', 'danger'));
      },
    });
  }

  protected async removeProfesor(profesor: Profesor): Promise<void> {
    const confirmed = await this.confirmAction(
      'Quitar profesor',
      `¿Quitarle el acceso a ${profesor.name}?`,
      'Quitar acceso',
    );
    if (!confirmed) {
      return;
    }
    this.removingProfesorId.set(profesor.id);
    this.profesorService.remove(profesor.id).subscribe({
      next: () => {
        this.removingProfesorId.set(null);
        this.profesores.update((list) => list.filter((p) => p.id !== profesor.id));
        this.showToast(`Se le quitó el acceso a ${profesor.name}.`);
      },
      error: () => {
        this.removingProfesorId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos quitar el acceso. Intenta nuevamente.', 'danger'));
      },
    });
  }

  // ---- Co-admins: autoservicio, antes solo lo podía hacer el super-admin ----

  protected readonly admins = signal<Admin[]>([]);
  protected readonly newAdminName = signal('');
  protected readonly newAdminEmail = signal('');
  protected readonly addingAdmin = signal(false);
  protected readonly removingAdminId = signal<number | null>(null);

  private loadAdmins(): void {
    this.gymService.listMyAdmins().subscribe({ next: (list) => this.admins.set(list) });
  }

  protected addMyAdmin(): void {
    const name = this.newAdminName().trim();
    const email = this.newAdminEmail().trim();
    if (!name || !email) {
      return;
    }
    const payload: CreateAdminRequest = { name, email };
    this.addingAdmin.set(true);
    this.gymService.addMyAdmin(payload).subscribe({
      next: (admin) => {
        this.addingAdmin.set(false);
        this.admins.update((list) => [...list, admin]);
        this.newAdminName.set('');
        this.newAdminEmail.set('');
        this.showToast(`Invitamos a ${admin.name} por correo — ya puede entrar con Google.`);
      },
      error: (err: Error) => {
        this.addingAdmin.set(false);
        this.handleWriteError(() =>
          this.showToast(err.message || 'No pudimos agregar al administrador. Intenta nuevamente.', 'danger'),
        );
      },
    });
  }

  protected async removeMyAdmin(admin: Admin): Promise<void> {
    const confirmed = await this.confirmAction(
      'Quitar administrador',
      `¿Quitarle el acceso de administrador a ${admin.name}?`,
      'Quitar acceso',
    );
    if (!confirmed) {
      return;
    }
    this.removingAdminId.set(admin.id);
    this.gymService.removeMyAdmin(admin.id).subscribe({
      next: () => {
        this.removingAdminId.set(null);
        this.admins.update((list) => list.filter((a) => a.id !== admin.id));
        this.showToast(`Se le quitó el acceso a ${admin.name}.`);
      },
      error: () => {
        this.removingAdminId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos quitar el acceso. Intenta nuevamente.', 'danger'));
      },
    });
  }

  // ---- "Memoria Viva": rutina de un socio (GYM_ADMIN y PROFESOR, ver SecurityConfig) ----

  protected readonly workoutPlanMember = signal<Member | null>(null);
  protected readonly workoutPlan = signal<WorkoutPlan | null>(null);
  protected readonly workoutLatestLog = signal<MemberWorkoutLog | null>(null);
  protected readonly loadingWorkoutPlan = signal(false);
  protected readonly savingWorkoutPlan = signal(false);

  protected openWorkoutPlanModal(member: Member): void {
    this.workoutPlanMember.set(member);
    this.workoutPlan.set(null);
    this.workoutLatestLog.set(null);
    this.loadingWorkoutPlan.set(true);
    this.workoutService.getActivePlan(member.id).subscribe({
      next: (plan) => this.workoutPlan.set(plan),
      error: () => this.showToast('No pudimos cargar la rutina. Intenta nuevamente.', 'danger'),
    });
    this.workoutService.getLatestLog(member.id).subscribe({
      next: (log) => {
        this.workoutLatestLog.set(log);
        this.loadingWorkoutPlan.set(false);
      },
      error: () => this.loadingWorkoutPlan.set(false),
    });
  }

  protected closeWorkoutPlanModal(): void {
    this.workoutPlanMember.set(null);
  }

  // Si ya había una rutina activa, "Guardar" la reemplaza entera (desactiva la vieja, crea una
  // nueva) — pedido explícito de UX: confirmar antes, porque desde la pantalla se siente como
  // "editar", no como "reemplazar", aunque los registros viejos queden intactos como historia.
  protected async confirmWorkoutPlan(request: CreateWorkoutPlanRequest): Promise<void> {
    const member = this.workoutPlanMember();
    if (!member) {
      return;
    }
    if (this.workoutPlan() !== null) {
      const confirmed = await this.confirmAction(
        'Reemplazar rutina',
        `${member.name} ya tiene una rutina activa — esto la reemplaza entera por la nueva. Los registros ya hechos quedan intactos, como historia.`,
        'Reemplazar',
      );
      if (!confirmed) {
        return;
      }
    }
    this.savingWorkoutPlan.set(true);
    this.workoutService.replacePlan(member.id, request).subscribe({
      next: () => {
        this.savingWorkoutPlan.set(false);
        this.workoutPlanMember.set(null);
        this.showToast(`Rutina de ${member.name} actualizada.`);
      },
      error: () => {
        this.savingWorkoutPlan.set(false);
        this.handleWriteError(() => this.showToast('No pudimos guardar la rutina. Intenta nuevamente.', 'danger'));
      },
    });
  }

  private async showToast(message: string, color: 'success' | 'danger' | 'warning' = 'success'): Promise<void> {
    const toast = await this.toastController.create({
      message,
      duration: 4000,
      position: 'bottom',
      color,
    });
    await toast.present();
  }

  // Cualquier escritura de un DEMO_ADMIN falla con un 403 real del backend (ver SecurityConfig
  // — ningún endpoint bajo /api/gym-admin/** que no sea GET admite ese rol), nunca por un
  // problema real del sistema. En vez de mostrarle "Ocurrió un error, intenta nuevamente" (sugiere
  // que algo se rompió), se le recuerda que está en una demo de solo lectura. `onRealError` corre
  // sin cambios para cualquier otro admin — mismo comportamiento de siempre para un error real.
  private handleWriteError(onRealError: () => void): void {
    if (this.isDemoAdmin()) {
      this.showToast('Estás en una cuenta demo — esta acción está deshabilitada a propósito.', 'warning');
      return;
    }
    onRealError();
  }

  // Al salir, un admin de gimnasio vuelve a la página propia de SU gimnasio
  // (no al /login genérico de mygym) — más contextual, y esa página ya sabe
  // re-loguearlo si vuelve a tocar "Continuar con Google" (el backend de
  // /join detecta que el email ya existe y solo lo loguea, sin re-provisionar).
  protected goToDemoPreview(): void {
    this.router.navigate(['/gym-admin/demo-preview']);
  }

  protected async logout(): Promise<void> {
    await this.authService.logout();
    const slug = this.gymSlug();
    this.router.navigate([slug ? `/j/${slug}` : '/login']);
  }

  protected readonly joinUrl = computed(() => {
    const slug = this.gymSlug();
    return slug ? `mygym.cl/j/${slug}` : '';
  });

  protected async copyJoinLink(): Promise<void> {
    const slug = this.gymSlug();
    if (!slug) {
      return;
    }
    try {
      await navigator.clipboard.writeText(`https://www.mygym.cl/j/${slug}`);
      this.showToast('Link copiado.');
    } catch {
      this.showToast('No pudimos copiar el link. Cópialo a mano.', 'danger');
    }
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
        });
        this.bookingRules.set({
          bookingWindowMinutes: gym.bookingWindowMinutes,
          cancellationWindowMinutes: gym.cancellationWindowMinutes,
          waitlistHeadStartMinutes: gym.waitlistHeadStartMinutes,
          showAttendeesToMembers: gym.showAttendeesToMembers,
        });
        const knownBank = gym.bankName && (this.chileBanks as readonly string[]).includes(gym.bankName);
        this.bankTransferForm.patchValue({
          bankName: gym.bankName ? (knownBank ? gym.bankName : 'Otro') : '',
          bankNameOther: gym.bankName && !knownBank ? gym.bankName : '',
          accountType: gym.bankAccountType ?? '',
          accountNumber: gym.bankAccountNumber ?? '',
          holderRut: gym.bankHolderRut ?? '',
          holderName: gym.bankHolderName ?? '',
          confirmationEmail: gym.bankConfirmationEmail ?? '',
        });
        this.gymLoaded.set(true);
        this.loadFlowAccount();
      },
      error: () => {
        this.showToast('No pudimos cargar los datos del gimnasio. Intenta nuevamente.', 'danger');
        this.gymLoaded.set(true);
      },
    });
  }

  private loadFlowAccount(): void {
    this.gymService.getMyFlowAccount().subscribe({
      next: (account) => {
        this.flowAccount.set(account);
        this.flowAccountForm.patchValue({
          companyRut: account.companyRut ?? '',
          companyName: account.companyName ?? '',
          businessActivity: account.businessActivity ?? '',
          companyAddress: account.companyAddress ?? '',
          vatCondition: account.vatCondition ?? '',
          legalRepName: account.legalRepName ?? '',
          legalRepRut: account.legalRepRut ?? '',
          legalRepPhone: account.legalRepPhone ?? '',
          contactEmail: account.contactEmail ?? '',
          contactName: account.contactName ?? '',
          contactPhone: account.contactPhone ?? '',
        });
      },
      error: () => {
        // Best-effort: si falla, la sección queda vacía pero el resto del panel sigue andando.
      },
    });
  }

  private loadPhotos(): void {
    this.gymService.listMyPhotos().subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => this.showToast('No pudimos cargar las fotos. Intenta nuevamente.', 'danger'),
    });
  }

  private loadBlocks(): void {
    this.gymService.listMyBlocks().subscribe({
      next: (blocks) => this.blocks.set(sortBlocksBySchedule(blocks)),
      error: () => this.showToast('No pudimos cargar los horarios. Intenta nuevamente.', 'danger'),
    });
  }

  private loadPlans(): void {
    this.gymService.listMyPlans().subscribe({
      next: (plans) => this.plans.set(plans),
      error: () => this.showToast('No pudimos cargar los planes. Intenta nuevamente.', 'danger'),
    });
  }

  private loadMembers(): void {
    this.memberList.reload();
  }

  private loadTvScreens(): void {
    this.tvScreenService.listMyScreens().subscribe({
      next: (screens) => this.tvScreens.set(screens),
      error: () => this.showToast('No pudimos cargar las pantallas. Intenta nuevamente.', 'danger'),
    });
  }

  protected submitTvScreen(): void {
    if (this.tvScreenForm.invalid) {
      return;
    }
    const raw = this.tvScreenForm.getRawValue();
    this.tvScreenError.set(null);
    this.claimingScreen.set(true);
    this.tvScreenService.claimScreen({ code: raw.code.trim().toUpperCase(), name: raw.name.trim() }).subscribe({
      next: (screen) => {
        this.claimingScreen.set(false);
        this.tvScreens.update((list) => [...list, screen]);
        this.tvScreenForm.reset({ code: '', name: '' });
        this.showToast(`Pantalla "${screen.name}" vinculada.`);
      },
      error: (err) => {
        this.claimingScreen.set(false);
        this.handleWriteError(() =>
          this.tvScreenError.set(
            err?.status === 404
              ? 'Ese código no existe o ya venció — recarga la pantalla de la TV para que muestre uno nuevo.'
              : 'No pudimos vincular la pantalla. Intenta nuevamente.',
          ),
        );
      },
    });
  }

  protected async removeTvScreen(screen: TvScreen): Promise<void> {
    const confirmed = await this.confirmAction(
      'Desvincular pantalla',
      `¿Desvincular "${screen.name}"? La TV va a dejar de mostrar el horario hasta que la vincules de nuevo con un código nuevo.`,
      'Desvincular',
    );
    if (!confirmed) {
      return;
    }
    this.removingScreenId.set(screen.id);
    this.tvScreenService.removeScreen(screen.id).subscribe({
      next: () => {
        this.removingScreenId.set(null);
        this.tvScreens.update((list) => list.filter((s) => s.id !== screen.id));
        this.showToast(`Pantalla "${screen.name}" desvinculada.`);
      },
      error: () => {
        this.removingScreenId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos desvincular la pantalla.', 'danger'));
      },
    });
  }

  private loadClosures(): void {
    this.closureList.reload();
  }

  protected openClosureModal(): void {
    this.closurePreview.set(null);
    this.closurePreviewError.set(null);
    this.isClosureModalOpen.set(true);
  }

  protected closeClosureModal(): void {
    this.isClosureModalOpen.set(false);
  }

  protected requestClosurePreview(request: GymClosureCreateRequest): void {
    this.closurePreview.set(null);
    this.closurePreviewError.set(null);
    this.closurePreviewing.set(true);
    this.gymService.previewMyClosure(request).subscribe({
      next: (preview) => {
        this.closurePreviewing.set(false);
        this.closurePreview.set(preview);
      },
      error: (err) => {
        this.closurePreviewing.set(false);
        // Mismo criterio que handleWriteError, pero el mensaje va DENTRO del modal (nota inline)
        // en vez de un toast — con el modal abierto tapando la pantalla, un toast de fondo puede
        // pasar desapercibido y la vista de confirmación quedaría en blanco sin esto.
        this.closurePreviewError.set(
          this.isDemoAdmin()
            ? 'Estás en una cuenta demo — esta acción está deshabilitada a propósito.'
            : err?.message || 'No pudimos calcular el impacto. Intenta nuevamente.',
        );
      },
    });
  }

  protected confirmClosure(request: GymClosureCreateRequest): void {
    this.closureSaving.set(true);
    this.gymService.createMyClosure(request).subscribe({
      next: (closure) => {
        this.closureSaving.set(false);
        this.isClosureModalOpen.set(false);
        this.loadClosures();
        this.showToast(
          `Gimnasio cerrado: ${closure.cancelledReservationsCount} reserva(s) canceladas, ${closure.affectedMembersCount} socio(s) van a recibir un email.`,
        );
      },
      error: (err) => {
        this.closureSaving.set(false);
        this.handleWriteError(() => this.showToast(err.message || 'No pudimos aplicar el cierre. Intenta nuevamente.', 'danger'));
      },
    });
  }

  protected async liftClosure(closure: GymClosure): Promise<void> {
    const confirmed = await this.confirmAction(
      'Levantar cierre',
      '¿Volver a permitir reservas desde hoy? Las reservas ya canceladas por este cierre no se restauran.',
      'Levantar',
    );
    if (!confirmed) {
      return;
    }
    this.liftingClosureId.set(closure.id);
    this.gymService.liftMyClosure(closure.id).subscribe({
      next: () => {
        this.liftingClosureId.set(null);
        this.loadClosures();
        this.showToast('Cierre levantado — ya se puede volver a reservar desde hoy.');
      },
      error: () => {
        this.liftingClosureId.set(null);
        this.handleWriteError(() => this.showToast('No pudimos levantar el cierre.', 'danger'));
      },
    });
  }

  protected openEditClosureModal(closure: GymClosure): void {
    this.editClosurePreview.set(null);
    this.editClosurePreviewError.set(null);
    this.editingClosure.set(closure);
  }

  protected closeEditClosureModal(): void {
    this.editingClosure.set(null);
  }

  protected requestEditClosurePreview(request: GymClosureUpdateRequest): void {
    const closure = this.editingClosure();
    if (!closure) {
      return;
    }
    this.editClosurePreview.set(null);
    this.editClosurePreviewError.set(null);
    this.editClosurePreviewing.set(true);
    this.gymService.previewUpdateMyClosure(closure.id, request).subscribe({
      next: (preview) => {
        this.editClosurePreviewing.set(false);
        this.editClosurePreview.set(preview);
      },
      error: (err) => {
        this.editClosurePreviewing.set(false);
        this.editClosurePreviewError.set(
          this.isDemoAdmin()
            ? 'Estás en una cuenta demo — esta acción está deshabilitada a propósito.'
            : err?.message || 'No pudimos calcular el impacto. Intenta nuevamente.',
        );
      },
    });
  }

  protected confirmEditClosure(request: GymClosureUpdateRequest): void {
    const closure = this.editingClosure();
    if (!closure) {
      return;
    }
    this.editClosureSaving.set(true);
    this.gymService.updateMyClosure(closure.id, request).subscribe({
      next: (updated) => {
        this.editClosureSaving.set(false);
        this.editingClosure.set(null);
        this.closures.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
        this.showToast('Cierre actualizado.');
      },
      error: (err) => {
        this.editClosureSaving.set(false);
        this.handleWriteError(() => this.showToast(err.message || 'No pudimos editar el cierre. Intenta nuevamente.', 'danger'));
      },
    });
  }

  protected closureStatus(closure: GymClosure): { label: string; color: string } {
    const today = todayIsoDate();
    if (!closure.active) {
      return closure.liftedAt ? { label: 'Levantado', color: 'medium' } : { label: 'Finalizado', color: 'medium' };
    }
    if (today < closure.startDate) {
      return { label: 'Programado', color: 'warning' };
    }
    return { label: 'Activo', color: 'danger' };
  }

  // Mismo criterio que lastLoginLabel/demoExpiryLabel en gym-form.ts (super-admin) — "última
  // actividad" relativa, más rápido de leer que una fecha. Acá no hay vencimiento fijo que
  // mostrar (la pantalla no expira por tiempo, solo por abandono, ver TvScreenService), así que
  // solo se muestra la actividad.
  protected screenActivityLabel(screen: TvScreen): string {
    if (!screen.lastPolledAt) {
      return 'Todavía no se conectó';
    }
    const elapsedMs = Date.now() - new Date(screen.lastPolledAt).getTime();
    if (elapsedMs < 2 * 60 * 1000) {
      return 'Conectada ahora';
    }
    const hours = Math.round(elapsedMs / (60 * 60 * 1000));
    if (hours < 1) {
      return `Última vez hace ${Math.round(elapsedMs / (60 * 1000))} min`;
    }
    if (hours < 24) {
      return `Última vez hace ${hours}h`;
    }
    const days = Math.round(hours / 24);
    return `Última vez hace ${days} día${days === 1 ? '' : 's'}`;
  }
}
