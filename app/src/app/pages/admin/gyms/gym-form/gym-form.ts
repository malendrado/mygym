import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonBackButton,
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
  IonNote,
  IonSegment,
  IonSegmentButton,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonText,
  IonTextarea,
  IonTitle,
  IonToggle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  barbellOutline,
  businessOutline,
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
  eyeOutline,
  helpCircleOutline,
  hourglassOutline,
  imagesOutline,
  logoGoogle,
  megaphoneOutline,
  paperPlaneOutline,
  peopleOutline,
  personCircleOutline,
  personOutline,
  pricetagOutline,
  refreshOutline,
  removeCircleOutline,
  searchOutline,
  settingsOutline,
  sparklesOutline,
  timeOutline,
  trashOutline,
  warningOutline,
} from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { GymService } from '../../../../core/services/gym.service';
import { MemberService } from '../../../../core/services/member.service';
import {
  Admin,
  BANK_ACCOUNT_TYPES,
  BankTransferUpdateRequest,
  BlockOccurrenceAttendees,
  BrandingSuggestion,
  CHILE_BANKS,
  CreateGymBlockRequest,
  CreateGymPhotoRequest,
  CreateGymPlanRequest,
  DayOfWeek,
  Gym,
  GymBlock,
  GymPhoto,
  GymPlan,
  UpdateGymBlockRequest,
  UpdateGymPlanRequest,
  sortBlocksBySchedule,
} from '../../../../core/models/gym.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Attendee, InviteStatus, Member, MembershipStatus } from '../../../../core/models/member.model';
import { BloqueFormModal, DAYS } from '../bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../bloque-series-modal/bloque-series-modal';
import { PlanFormModal } from '../plan-form-modal/plan-form-modal';
import { MarkPaidModal } from '../mark-paid-modal/mark-paid-modal';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../../../core/utils/class-category';
import {
  LIGHT_PALETTES,
  LightPaletteEntry,
  ThemeMode,
  clearThemeOverrides,
  deriveSurfaceTint,
  ensureMinContrastColor,
  syncThemeOverrides,
} from '../../../../core/utils/gym-theme';

registerClassCategoryIcons();

addIcons({
  'business-outline': businessOutline,
  'settings-outline': settingsOutline,
  'time-outline': timeOutline,
  'calendar-outline': calendarOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'chevron-up-outline': chevronUpOutline,
  'chevron-down-outline': chevronDownOutline,
  'people-outline': peopleOutline,
  'eye-outline': eyeOutline,
  'paper-plane-outline': paperPlaneOutline,
  'sparkles-outline': sparklesOutline,
  'person-outline': personOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'hourglass-outline': hourglassOutline,
  'close-circle-outline': closeCircleOutline,
  'help-circle-outline': helpCircleOutline,
  'logo-google': logoGoogle,
  'barbell-outline': barbellOutline,
  'create-outline': createOutline,
  'trash-outline': trashOutline,
  'pricetag-outline': pricetagOutline,
  'color-palette-outline': colorPaletteOutline,
  'refresh-outline': refreshOutline,
  'remove-circle-outline': removeCircleOutline,
  'search-outline': searchOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'images-outline': imagesOutline,
  'megaphone-outline': megaphoneOutline,
  'person-circle-outline': personCircleOutline,
  'warning-outline': warningOutline,
});

type Status = 'idle' | 'loading' | 'saving' | 'error';
type SuggestStatus = 'idle' | 'loading' | 'error';
type Section = 'general' | 'blocks' | 'plans' | 'members' | 'branding' | 'history';

// Mismo criterio que gym-admin.ts (implementación paralela, no compartida)
// — segundo eje de filtro para la grilla de horarios, con tooltip que
// explica el rango exacto de cada franja.
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

// Mismo criterio que gym-admin.ts — un bloque es una plantilla semanal sin
// fecha, "quién reservó" necesita la PRÓXIMA ocurrencia real de ese día.
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

// Mismo criterio que gym-admin.ts (implementación paralela, no compartida).
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

interface Palette {
  key: string;
  label: string;
  hex: string;
  contrast: string;
}

// Mirrors gym-admin.ts's PALETTES (mismas 12 opciones, mismo criterio de
// contraste) — y GymPalette.java en el backend. Si una cambia, actualizar las 3.
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

const MAX_PHOTO_DIMENSION = 1600;
const ACCEPTED_PHOTO_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_PHOTOS = 8;
const MAX_LOGO_DIMENSION = 256;
const ACCEPTED_LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];

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
  selector: 'app-gym-form',
  imports: [
    ReactiveFormsModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonBackButton,
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
    IonTextarea,
    IonToggle,
    IonNote,
    IonText,
    IonList,
    IonBadge,
    IonChip,
    IonModal,
    IonSegment,
    IonSegmentButton,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    BloqueFormModal,
    BloqueSeriesModal,
    PlanFormModal,
    MarkPaidModal,
    QuantityStepper,
  ],
  templateUrl: './gym-form.html',
  styleUrl: './gym-form.scss',
})
export class GymForm implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gymService = inject(GymService);
  private readonly memberService = inject(MemberService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);

  protected readonly status = signal<Status>('idle');
  protected readonly section = signal<Section>('general');
  protected readonly sectionLabel = computed(() => SECTION_LABELS[this.section()]);
  protected readonly gymId = signal<number | null>(null);
  protected readonly hasPublicId = signal(false);
  protected readonly gymLoaded = signal(false);
  protected readonly blocks = signal<GymBlock[]>([]);
  // Mismo fix que gym-admin.ts (2026-09-13): sin esto la lista de bloques de
  // TODOS los días se mostraba de corrido — el super-admin ve esta misma
  // pantalla (es una implementación paralela a la de gym-admin, no la
  // comparten), así que necesitaba el mismo filtro.
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
  protected readonly isModalOpen = signal(false);
  protected readonly editingBlock = signal<GymBlock | null>(null);
  protected readonly isSeriesModalOpen = signal(false);
  protected readonly seriesCreating = signal(false);
  protected readonly admins = signal<Admin[]>([]);
  protected readonly adminError = signal<string | null>(null);

  // Acceso de solo-lectura a la demo comercial (Role.DEMO_ADMIN) — ver DemoPreviewController/
  // SecurityConfig en el backend. Espejo exacto del bloque de admins de arriba.
  protected readonly demoAdmins = signal<Admin[]>([]);
  protected readonly demoAdminError = signal<string | null>(null);

  // Planes (pestaña Planes) — misma lógica que gym-admin.ts, pero con gymId
  // explícito (el de la ruta) en vez de tomarlo del JWT del gym-admin logueado.
  // Clave de cache con fecha incluida — ver comentario largo en gym-admin.ts.
  protected readonly expandedAttendeesKey = signal<string | null>(null);
  protected readonly attendeesByKey = signal<Record<string, Attendee[]>>({});
  protected readonly loadingAttendeesKey = signal<string | null>(null);
  protected readonly historyDate = signal(
    new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date()),
  );
  protected readonly historyLoading = signal(false);
  protected readonly expandedHistoryDay = signal<string | null>(null);

  // Semana en vez de un solo día — ver comentario largo en gym-admin.ts
  // (esta es la versión super-admin, mismo mecanismo, gymId explícito).
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

  // Buscador de reservas futuras por nombre de socio — mismo mecanismo que gym-admin.ts (ver
  // ese archivo para el porqué: no es un filtro client-side, los rosters no están cargados
  // completos en memoria). Mientras hay una búsqueda activa (≥2 caracteres), reemplaza la
  // vista semanal normal de Historial por la lista de coincidencias.
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
  protected readonly isPlanModalOpen = signal(false);
  protected readonly editingPlan = signal<GymPlan | null>(null);

  // Socios (pestaña Socios)
  protected readonly members = signal<Member[]>([]);
  protected readonly activeMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'ACTIVE').length);
  protected readonly expiringSoonMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'EXPIRING_SOON').length);
  protected readonly expiredMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'EXPIRED').length);
  protected readonly unpaidMembers = computed(() => this.members().filter((m) => m.membershipStatus === 'UNPAID').length);
  protected readonly memberStatusFilter = signal<MembershipStatus | null>(null);
  // Ver comentario largo en gym-admin.ts: las 6 calugas son un único grupo de filtro
  // mutuamente excluyente — click en cualquiera reemplaza cualquier filtro activo del otro eje.
  // "Invitados registrados" se retiró como bucket/filtro propio (mismo criterio que gym-admin.ts)
  // — el badge por fila se mantiene siempre, incluso ya activo, como info histórica.
  protected readonly invitedPendingMembers = computed(() => this.members().filter((m) => m.inviteStatus === 'PENDING').length);
  protected readonly memberInviteFilter = signal<InviteStatus>(null);
  // Buscador libre por nombre/email — independiente de las calugas de estado/invitación,
  // se combinan todos con AND (mismo criterio que ya usa reservationSearchQuery en Historial).
  protected readonly memberSearch = signal('');
  protected readonly filteredMembers = computed(() => {
    const statusFilter = this.memberStatusFilter();
    const inviteFilter = this.memberInviteFilter();
    const query = this.memberSearch().trim().toLowerCase();
    return this.members().filter(
      (m) =>
        (!statusFilter || m.membershipStatus === statusFilter) &&
        (!inviteFilter || m.inviteStatus === inviteFilter) &&
        (!query || m.name.toLowerCase().includes(query) || m.email.toLowerCase().includes(query)),
    );
  });
  protected readonly memberForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  // Marca (pestaña Marca) — color + identidad + fotos. El logo sigue
  // editándose en Configuración (ya existía ahí como textarea de SVG); no se
  // duplica acá para no tener dos editores del mismo campo.
  protected readonly themeColor = signal<string>(PALETTES[0].hex);
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
  protected readonly themeAccentTextSafe = computed(() =>
    ensureMinContrastColor(this.themeColor(), this.themeSurface().card),
  );
  protected readonly paletteOptions = signal<Palette[]>(randomSample(PALETTES, 4));
  protected readonly themeSaving = signal(false);

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

  protected readonly chileBanks = CHILE_BANKS;
  protected readonly bankAccountTypes = BANK_ACCOUNT_TYPES;
  protected readonly bankTransferForm = new FormGroup({
    bankName: new FormControl('', { nonNullable: true }),
    bankNameOther: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    accountType: new FormControl('', { nonNullable: true }),
    accountNumber: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
    holderRut: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20)] }),
    holderName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    confirmationEmail: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(160)] }),
  });
  protected readonly bankTransferSaving = signal(false);

  protected readonly photos = signal<GymPhoto[]>([]);
  protected readonly photoUploading = signal(false);
  protected readonly maxPhotos = MAX_PHOTOS;

  protected readonly suggestStatus = signal<SuggestStatus>('idle');
  protected readonly brandingSuggestion = signal<BrandingSuggestion | null>(null);
  protected readonly safeSuggestedLogo = computed(() => {
    const suggestion = this.brandingSuggestion();
    return suggestion ? this.sanitizer.bypassSecurityTrustHtml(suggestion.logoSvg) : null;
  });

  protected readonly createForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(120)] }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/), Validators.maxLength(60)],
    }),
    maxUsers: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(10000)] }),
    ownerName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(150)] }),
    ownerEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  protected readonly configForm = new FormGroup({
    active: new FormControl(true, { nonNullable: true }),
    maxUsers: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(10000)] }),
    googleLoginEnabled: new FormControl(false, { nonNullable: true }),
    logoSvg: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(20000)] }),
  });

  protected readonly adminForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(150)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  protected readonly demoAdminForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(150)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  protected readonly gym = signal<Gym | null>(null);
  protected readonly gymName = computed(() => this.gym()?.name ?? '');
  protected readonly gymSlug = computed(() => this.gym()?.slug ?? '');
  // "Acceso a la demo" es solo para el/los gym de demo comercial (nombre con "Demo" explícito,
  // ej. "mygym Demo") — pedido explícito del usuario: se coló mostrada para TODOS los gyms
  // (2026-09-23), incluido uno de prueba real "QA Payment Flow" sin ninguna relación con la demo.
  protected readonly isDemoGym = computed(() => this.gymName().toLowerCase().includes('demo'));
  protected readonly isGymLogoRaster = computed(() => (this.gym()?.logoSvg ?? '').startsWith('data:image'));
  protected readonly safeGymLogo = computed(() => {
    const logo = this.gym()?.logoSvg;
    return logo && !this.isGymLogoRaster() ? this.sanitizer.bypassSecurityTrustHtml(logo) : null;
  });

  constructor() {
    const publicId = this.route.snapshot.paramMap.get('publicId');
    if (publicId) {
      this.hasPublicId.set(true);
      this.resolveAndLoadGym(publicId);
    }

    // Mismo mecanismo que gym-admin.ts: los overlays de Ionic (ion-select,
    // ion-alert, ion-toast) se portan fuera de <ion-content>/<ion-modal>, así
    // que las variables de tema se setean en <html> mientras esta página
    // está activa, y se limpian al salir para no filtrar el color de ESTE
    // gimnasio a otras pantallas del super-admin (gym-list, otro gym-form).
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

    // Bloques y gymId llegan async — si el super-admin entra a Historial
    // antes de que respondan, hay que reintentar en cuanto estén listos.
    effect(() => {
      this.blocks();
      this.historyDate();
      this.gymId();
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

  protected get isEditing(): boolean {
    return this.hasPublicId();
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

  /**
   * La URL solo trae el UUID opaco — nunca el id secuencial de la tabla, ver
   * comentario en gym.model.ts (Gym.publicId). Este es el único punto donde
   * se resuelve ese UUID a un id numérico; de ahí en adelante todo el resto
   * de la página (bloques, planes, socios, fotos) usa ese id resuelto, nunca
   * el de la URL.
   */
  private resolveAndLoadGym(publicId: string): void {
    this.status.set('loading');
    this.gymService.getByPublicId(publicId).subscribe({
      next: (gym) => {
        const id = gym.id;
        this.gymId.set(id);
        this.gym.set(gym);
        this.configForm.setValue({
          active: gym.active,
          maxUsers: gym.maxUsers,
          googleLoginEnabled: gym.googleLoginEnabled,
          logoSvg: gym.logoSvg ?? '',
        });
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
        this.status.set('idle');
        this.gymLoaded.set(true);
        this.loadBlocks(id);
        this.loadAdmins(id);
        this.loadDemoAdmins(id);
        this.loadPlans(id);
        this.loadMembers(id);
        this.loadPhotos(id);
      },
      error: () => {
        this.status.set('error');
        this.gymLoaded.set(true);
      },
    });
  }

  private loadBlocks(id: number): void {
    this.gymService.listBlocks(id).subscribe({
      next: (blocks) => this.blocks.set(sortBlocksBySchedule(blocks)),
      error: () => this.status.set('error'),
    });
  }

  private loadAdmins(id: number): void {
    this.gymService.listAdmins(id).subscribe({
      next: (admins) => this.admins.set(admins),
      error: () => this.status.set('error'),
    });
  }

  private loadDemoAdmins(id: number): void {
    this.gymService.listDemoAdmins(id).subscribe({
      next: (demoAdmins) => this.demoAdmins.set(demoAdmins),
      error: () => this.status.set('error'),
    });
  }

  protected suggestBranding(): void {
    const name = this.createForm.controls.name.value.trim();
    if (!name) {
      return;
    }
    this.suggestStatus.set('loading');
    this.gymService.suggestBranding(name).subscribe({
      next: (suggestion) => {
        this.brandingSuggestion.set(suggestion);
        this.suggestStatus.set('idle');
      },
      error: () => this.suggestStatus.set('error'),
    });
  }

  protected discardSuggestion(): void {
    this.brandingSuggestion.set(null);
  }

  protected submitCreate(): void {
    if (this.createForm.invalid) {
      return;
    }
    this.status.set('saving');
    const suggestion = this.brandingSuggestion();
    const payload = {
      ...this.createForm.getRawValue(),
      themeColor: suggestion?.themeColor ?? null,
      logoSvg: suggestion?.logoSvg ?? null,
    };
    this.gymService.create(payload).subscribe({
      next: (gym: Gym) => this.router.navigate(['/admin/gyms', gym.publicId]),
      error: () => this.status.set('error'),
    });
  }

  protected submitConfig(): void {
    const id = this.gymId();
    if (id === null || this.configForm.invalid) {
      return;
    }
    this.status.set('saving');
    const value = this.configForm.getRawValue();
    this.gymService.updateConfig(id, { ...value, logoSvg: value.logoSvg.trim() || null }).subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.status.set('idle');
        this.showToast('Configuración guardada.');
      },
      error: () => this.status.set('error'),
    });
  }

  protected isConfigLogoRaster(value: string): boolean {
    return value.startsWith('data:image');
  }

  protected safeConfigLogo(value: string): SafeHtml | null {
    return value && !this.isConfigLogoRaster(value) ? this.sanitizer.bypassSecurityTrustHtml(value) : null;
  }

  protected initials(name: string): string {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase();
  }

  protected submitAdmin(): void {
    const id = this.gymId();
    if (id === null || this.adminForm.invalid) {
      return;
    }
    this.status.set('saving');
    this.adminError.set(null);
    this.gymService.addAdmin(id, this.adminForm.getRawValue()).subscribe({
      next: (admin) => {
        this.adminForm.reset({ name: '', email: '' });
        this.status.set('idle');
        this.loadAdmins(id);
        this.showToast(`Administrador agregado. Le enviamos un email a ${admin.email} con el acceso.`);
      },
      error: (err: Error) => {
        this.status.set('idle');
        this.adminError.set(err.message || 'No pudimos agregar al administrador. Intenta nuevamente.');
      },
    });
  }

  protected submitDemoAdmin(): void {
    const id = this.gymId();
    if (id === null || this.demoAdminForm.invalid) {
      return;
    }
    this.status.set('saving');
    this.demoAdminError.set(null);
    this.gymService.addDemoAdmin(id, this.demoAdminForm.getRawValue()).subscribe({
      next: (admin) => {
        this.demoAdminForm.reset({ name: '', email: '' });
        this.status.set('idle');
        this.loadDemoAdmins(id);
        this.showToast(`Acceso a la demo otorgado. Le enviamos un email a ${admin.email}.`);
      },
      error: (err: Error) => {
        this.status.set('idle');
        this.demoAdminError.set(err.message || 'No pudimos otorgar el acceso a la demo. Intenta nuevamente.');
      },
    });
  }

  protected readonly removingDemoAdminId = signal<number | null>(null);

  // Borrado real, no un toggle activar/desactivar (a diferencia de un admin real abajo): un
  // acceso demo revocado tiene que liberar el email por completo, para que esa misma persona
  // pueda convertirse en admin real más adelante sin quedar bloqueada por "email duplicado".
  protected async removeDemoAdmin(admin: Admin): Promise<void> {
    const id = this.gymId();
    if (id === null || this.removingDemoAdminId() !== null) {
      return;
    }
    const confirmed = await this.confirmAction(
      'Quitar acceso a la demo',
      `¿Quitarle el acceso a la demo a ${admin.name}? Se borra por completo — si más adelante quieres darle acceso de nuevo (a la demo o como admin real), vas a poder hacerlo sin problema.`,
      'Quitar acceso',
    );
    if (!confirmed) {
      return;
    }
    this.removingDemoAdminId.set(admin.id);
    this.gymService.removeDemoAdmin(id, admin.id).subscribe({
      next: () => {
        this.removingDemoAdminId.set(null);
        this.loadDemoAdmins(id);
      },
      error: () => {
        this.removingDemoAdminId.set(null);
        this.status.set('error');
      },
    });
  }

  protected readonly togglingAdminId = signal<number | null>(null);

  protected async toggleAdminStatus(admin: Admin): Promise<void> {
    const id = this.gymId();
    if (id === null || this.togglingAdminId() !== null) {
      return;
    }
    if (admin.active) {
      const confirmed = await this.confirmAction(
        'Quitar acceso',
        `¿Quitarle el acceso a ${admin.name}? Va a poder recuperarlo más tarde con "Reactivar".`,
        'Quitar acceso',
      );
      if (!confirmed) {
        return;
      }
    }
    this.togglingAdminId.set(admin.id);
    this.gymService.updateAdminStatus(id, admin.id, !admin.active).subscribe({
      next: () => {
        this.togglingAdminId.set(null);
        this.loadAdmins(id);
      },
      error: () => {
        this.togglingAdminId.set(null);
        this.status.set('error');
      },
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

  protected dayLabel(day: DayOfWeek): string {
    return DAYS.find((d) => d.value === day)?.label ?? day;
  }

  protected categoryIcon(category: string | null): string {
    return resolveClassCategoryIcon(category);
  }

  protected selectDayFilter(day: DayOfWeek | null): void {
    this.selectedDay.set(day);
  }

  protected selectTimeBand(band: TimeBand | null): void {
    this.selectedTimeBand.set(band);
  }

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

  // Mismo motivo que en gym-admin.ts: nextOccurrenceDate() es una fecha
  // calendario pura, sin hora/huso — formatDate() la interpretaría como
  // medianoche UTC y se corre un día en husos negativos. Reordenar el
  // string a mano evita el problema por completo.
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
    const gymId = this.gymId();
    if (!gymId) {
      return;
    }
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
    this.gymService.getBlockAttendees(gymId, blockId, date).subscribe({
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
    const gymId = this.gymId();
    if (!gymId) {
      return;
    }
    const dates = this.historyWeekDates();
    const from = dates[0];
    const to = dates[dates.length - 1];
    const rangeKey = `${from}_${to}`;
    if (this.historyLoadedRanges.has(rangeKey)) {
      return;
    }
    this.historyLoadedRanges.add(rangeKey);
    this.historyLoading.set(true);
    this.gymService.getHistoryAttendees(gymId, from, to).subscribe({
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

  protected onMemberSearchInput(value: string): void {
    this.memberSearch.set(value);
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
    this.reservationSearchTimeout = setTimeout(() => this.runReservationSearch(trimmed), 300);
  }

  private runReservationSearch(query: string): void {
    const gymId = this.gymId();
    if (!gymId) {
      return;
    }
    this.reservationSearchLoading.set(true);
    this.gymService.searchReservations(gymId, query).subscribe({
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
    const gymId = this.gymId();
    if (!gymId) {
      return;
    }
    const confirmed = await this.confirmAction(
      'Cancelar reserva',
      `¿Cancelar la reserva de ${attendee.name} para esta clase? Se libera el cupo.`,
      'Cancelar reserva',
    );
    if (!confirmed) {
      return;
    }
    this.gymService.cancelReservationForGym(gymId, attendee.reservationId).subscribe({
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
    const id = this.gymId();
    if (id === null) {
      return;
    }
    this.isSeriesModalOpen.set(false);
    this.seriesCreating.set(true);
    let created = 0;
    let failed = 0;
    for (const draft of drafts) {
      try {
        await firstValueFrom(this.gymService.createBlock(id, draft));
        created++;
      } catch {
        failed++;
      }
    }
    this.seriesCreating.set(false);
    this.loadBlocks(id);
    this.showToast(
      failed === 0 ? `Se crearon ${created} bloques.` : `Se crearon ${created} bloques. ${failed} fallaron.`,
    );
  }

  protected saveBlock(payload: CreateGymBlockRequest | UpdateGymBlockRequest): void {
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const editing = this.editingBlock();
    const request = editing
      ? this.gymService.updateBlock(id, editing.id, payload as UpdateGymBlockRequest)
      : this.gymService.createBlock(id, payload as CreateGymBlockRequest);

    this.status.set('saving');
    request.subscribe({
      next: () => {
        this.status.set('idle');
        this.isModalOpen.set(false);
        this.loadBlocks(id);
      },
      error: () => this.status.set('error'),
    });
  }

  protected async removeBlock(block: GymBlock): Promise<void> {
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const confirmed = await this.confirmAction(
      'Eliminar bloque',
      `¿Eliminar "${block.label}"? Esta acción no se puede deshacer.`,
    );
    if (!confirmed) {
      return;
    }
    this.gymService.deleteBlock(id, block.id).subscribe({
      next: () => this.loadBlocks(id),
      error: () => this.status.set('error'),
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

  // ---- Planes (pestaña Planes) ----

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
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const editing = this.editingPlan();
    const request = editing
      ? this.gymService.updatePlan(id, editing.id, payload as UpdateGymPlanRequest)
      : this.gymService.createPlan(id, payload as CreateGymPlanRequest);

    this.status.set('saving');
    request.subscribe({
      next: () => {
        this.status.set('idle');
        this.isPlanModalOpen.set(false);
        this.loadPlans(id);
      },
      error: () => this.status.set('error'),
    });
  }

  protected async removePlan(plan: GymPlan): Promise<void> {
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const confirmed = await this.confirmAction('Eliminar plan', `¿Eliminar "${plan.name}"? Esta acción no se puede deshacer.`);
    if (!confirmed) {
      return;
    }
    this.gymService.deletePlan(id, plan.id).subscribe({
      next: () => this.loadPlans(id),
      error: () => this.status.set('error'),
    });
  }

  private loadPlans(id: number): void {
    this.gymService.listPlans(id).subscribe({
      next: (plans) => this.plans.set(plans),
      error: () => this.status.set('error'),
    });
  }

  // ---- Socios (pestaña Socios) ----

  protected submitMember(): void {
    const id = this.gymId();
    if (id === null || this.memberForm.invalid) {
      return;
    }
    this.status.set('saving');
    this.memberService.createForGym(id, this.memberForm.getRawValue()).subscribe({
      next: (member) => {
        this.memberForm.reset({ name: '', email: '' });
        this.status.set('idle');
        this.loadMembers(id);
        this.showToast(`Socio agregado: ${member.name}. Le enviamos un correo para activar su cuenta y elegir un plan.`);
      },
      error: () => this.status.set('error'),
    });
  }

  private loadMembers(id: number): void {
    this.memberService.listForGym(id).subscribe({
      next: (members) => this.members.set(members),
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
  protected readonly deletingMemberId = signal<number | null>(null);

  // Registro manual para dinero que no pasó por Flow.cl (efectivo/transferencia) — el
  // super-admin elige el plan que el socio pagó y queda persistido de verdad
  // (GymService.simulatePlanPayment).
  // Reemplaza el ion-alert de radios planas: el admin necesita ver el mismo detalle de
  // precio/cupo (las "calugas") que el socio ve antes de pagar en /member — un ion-alert no
  // admite HTML/ion-badge dentro de sus inputs, así que se usa un ion-modal con
  // app-mark-paid-modal (mismo componente compartido que gym-admin.ts).
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
    const id = this.gymId();
    const member = this.markPaidMember();
    if (id === null || !member) {
      return;
    }
    this.markingPaidId.set(member.id);
    this.memberService.markPaidForGym(id, member.id, { planId }).subscribe({
      next: () => {
        this.markingPaidId.set(null);
        this.markPaidMember.set(null);
        this.showToast(`Pago registrado para ${member.name}.`);
        this.loadMembers(id);
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
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const confirmed = await this.confirmAction(
      'Quitar plan',
      `¿Quitarle el plan a ${member.name}? Va a dejar de poder reservar clases hasta que pague de nuevo.`,
      'Quitar plan',
    );
    if (!confirmed) {
      return;
    }
    this.revokingPlanId.set(member.id);
    this.memberService.revokePlanForGym(id, member.id).subscribe({
      next: () => {
        this.revokingPlanId.set(null);
        this.showToast(`Se le quitó el plan a ${member.name}.`);
        this.loadMembers(id);
      },
      error: () => {
        this.revokingPlanId.set(null);
        this.showToast('No pudimos quitar el plan. Intenta nuevamente.', 'danger');
      },
    });
  }

  // Borrado permanente, solo super-admin — pensado para cuando el dueño del gym quiere
  // expulsar a un socio y borrar todo rastro suyo (reservas, historial, pagos), no solo
  // quitarle el plan. Confirmación explícita porque es irreversible.
  protected async deleteMember(member: Member): Promise<void> {
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const confirmed = await this.confirmAction(
      'Eliminar socio permanentemente',
      `¿Eliminar a ${member.name} (${member.email})? Esto borra TODO su registro: reservas, historial de clases y pagos. No se puede deshacer.`,
      'Eliminar para siempre',
    );
    if (!confirmed) {
      return;
    }
    this.deletingMemberId.set(member.id);
    this.memberService.deleteMemberForGym(id, member.id).subscribe({
      next: () => {
        this.deletingMemberId.set(null);
        this.members.update((list) => list.filter((m) => m.id !== member.id));
        this.showToast(`${member.name} fue eliminado permanentemente.`);
      },
      error: () => {
        this.deletingMemberId.set(null);
        this.showToast('No pudimos eliminar al socio. Intenta nuevamente.', 'danger');
      },
    });
  }

  // ---- Marca (pestaña Marca): color, identidad, fotos ----

  protected reshufflePalettes(): void {
    this.paletteOptions.set(randomSample(PALETTES, 4));
  }

  protected surfaceFor(palette: Palette): { bg: string; card: string } {
    return deriveSurfaceTint(palette.hex, 'DARK');
  }

  protected setThemeModeView(mode: ThemeMode): void {
    this.themeMode.set(mode);
  }

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
    const id = this.gymId();
    if (id === null || this.themeSaving()) {
      return;
    }
    const previousColor = this.themeColor();
    const previousMode = this.themeMode();
    this.themeColor.set(hex);
    this.themeMode.set(mode);
    this.themeSaving.set(true);
    this.gymService.updateTheme(id, { themeColor: hex, themeMode: mode }).subscribe({
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
    const id = this.gymId();
    if (id === null || this.identityForm.invalid) {
      return;
    }
    const raw = this.identityForm.getRawValue();
    this.identitySaving.set(true);
    this.gymService
      .updateIdentity(id, {
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

  protected saveBankTransfer(): void {
    const id = this.gymId();
    if (id === null || this.bankTransferForm.invalid) {
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
    this.gymService.updateBankTransfer(id, payload).subscribe({
      next: () => {
        this.bankTransferSaving.set(false);
        this.showToast('Datos bancarios actualizados.');
      },
      error: () => {
        this.bankTransferSaving.set(false);
        this.showToast('No pudimos guardar los datos bancarios. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected readonly logoUploading = signal(false);

  // Mismo patrón que gym-admin.ts (onLogoFileSelected) — el super-admin no tenía forma de subir
  // un archivo acá, solo pegar SVG/data-URI a mano en el textarea. SVG se guarda tal cual (texto),
  // una imagen rasterizada se redimensiona antes (mismo tope de 256px que usa el dueño del gym).
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
      const logo = file.type === 'image/svg+xml' ? await file.text() : await this.resizeLogoFile(file);
      this.configForm.controls.logoSvg.setValue(logo);
      this.submitConfig();
    } catch {
      this.showToast('No pudimos leer esa imagen. Intenta con otra.', 'danger');
    } finally {
      this.logoUploading.set(false);
    }
  }

  private resizeLogoFile(file: File): Promise<string> {
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

  protected async onPhotoFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    const id = this.gymId();
    if (!file || id === null) {
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
      const photo = await firstValueFrom(this.gymService.createPhoto(id, payload));
      this.photos.update((list) => [...list, photo]);
    } catch {
      this.showToast('No pudimos subir esa foto. Intenta con otra.', 'danger');
    } finally {
      this.photoUploading.set(false);
    }
  }

  protected async removePhoto(photo: GymPhoto): Promise<void> {
    const id = this.gymId();
    if (id === null) {
      return;
    }
    const confirmed = await this.confirmAction('Eliminar foto', '¿Eliminar esta foto de tus instalaciones?');
    if (!confirmed) {
      return;
    }
    this.gymService.deletePhoto(id, photo.id).subscribe({
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

  private loadPhotos(id: number): void {
    this.gymService.listPhotos(id).subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => this.status.set('error'),
    });
  }

  // ---- Zona de peligro: desvinculación permanente ----
  // Confirmación por texto (no el confirmAction() de OK/Cancel que se usa para el resto de
  // los borrados) — pedido explícito del usuario para una acción que borra TODO el gimnasio
  // de una vez, ver GymDisconnectionService en el backend.
  protected readonly isDisconnectModalOpen = signal(false);
  protected readonly disconnectConfirmName = signal('');
  protected readonly disconnecting = signal(false);
  protected readonly disconnectError = signal<string | null>(null);
  protected readonly disconnectNameMatches = computed(
    () => this.disconnectConfirmName().trim() === this.gymName(),
  );

  protected openDisconnectModal(): void {
    this.disconnectConfirmName.set('');
    this.disconnectError.set(null);
    this.isDisconnectModalOpen.set(true);
  }

  protected closeDisconnectModal(): void {
    if (this.disconnecting()) {
      return;
    }
    this.isDisconnectModalOpen.set(false);
  }

  protected onDisconnectConfirmInput(value: string): void {
    this.disconnectConfirmName.set(value);
  }

  protected confirmDisconnect(): void {
    const id = this.gymId();
    if (id === null || !this.disconnectNameMatches() || this.disconnecting()) {
      return;
    }
    this.disconnecting.set(true);
    this.disconnectError.set(null);
    this.gymService.disconnectGym(id, { confirmGymName: this.disconnectConfirmName().trim() }).subscribe({
      next: () => {
        this.disconnecting.set(false);
        this.isDisconnectModalOpen.set(false);
        // El gym ya no existe en el backend — se limpia el estado local ACÁ, no solo se confía
        // en que la navegación a /admin/gyms desmonte este componente a tiempo. Bug real
        // reportado por el usuario (2026-09-23): sin esto, quedaba viéndose el gym borrado
        // hasta un F5 manual.
        this.gym.set(null);
        this.gymLoaded.set(false);
        this.showToast('Gimnasio desvinculado y borrado para siempre.');
        this.router.navigateByUrl('/admin/gyms', { replaceUrl: true });
      },
      error: (err: HttpErrorResponse) => {
        this.disconnecting.set(false);
        if (err.status === 409) {
          this.disconnectError.set('El nombre no coincide exactamente. Revísalo e intenta de nuevo.');
        } else if (err.status === 502) {
          // No se pudo enviar el email con el detalle de los socios a algún admin — el backend
          // no borró nada (ver GymDisconnectionService), el mensaje ya viene listo para mostrar.
          this.disconnectError.set(err.error?.detail || 'No pudimos notificar a un administrador. Intenta nuevamente.');
        } else {
          this.disconnectError.set('No pudimos desvincular el gimnasio. Intenta nuevamente.');
        }
      },
    });
  }
}
