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
  businessOutline,
  bulbOutline,
  calendarOutline,
  cloudUploadOutline,
  colorPaletteOutline,
  createOutline,
  imagesOutline,
  megaphoneOutline,
  peopleOutline,
  personAddOutline,
  personCircleOutline,
  pricetagOutline,
  refreshOutline,
  timeOutline,
  trashOutline,
} from 'ionicons/icons';
import { QuantityStepper } from '../../core/components/quantity-stepper/quantity-stepper';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { MemberService } from '../../core/services/member.service';
import {
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
import { Member } from '../../core/models/member.model';
import { BloqueFormModal, DAYS } from '../admin/gyms/bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../admin/gyms/bloque-series-modal/bloque-series-modal';
import { PlanFormModal } from '../admin/gyms/plan-form-modal/plan-form-modal';
import { deriveSurfaceTint, ensureMinContrastColor } from '../../core/utils/gym-theme';

addIcons({
  'time-outline': timeOutline,
  'calendar-outline': calendarOutline,
  'people-outline': peopleOutline,
  'person-add-outline': personAddOutline,
  'person-circle-outline': personCircleOutline,
  'color-palette-outline': colorPaletteOutline,
  'pricetag-outline': pricetagOutline,
  'refresh-outline': refreshOutline,
  'business-outline': businessOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'create-outline': createOutline,
  'trash-outline': trashOutline,
  'bulb-outline': bulbOutline,
  'megaphone-outline': megaphoneOutline,
  'images-outline': imagesOutline,
});

type Status = 'idle' | 'loading' | 'saving' | 'error';
type Section = 'blocks' | 'plans' | 'members' | 'branding';

const SECTION_LABELS: Record<Section, string> = {
  blocks: 'Horarios',
  plans: 'Planes',
  members: 'Socios',
  branding: 'Marca',
};

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
    IonModal,
    IonSegment,
    IonSegmentButton,
    IonText,
    IonTextarea,
    BloqueFormModal,
    BloqueSeriesModal,
    PlanFormModal,
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
  protected readonly section = signal<Section>('blocks');
  protected readonly sectionLabel = computed(() => SECTION_LABELS[this.section()]);
  protected readonly adminFirstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'admin');
  protected readonly tip = ADMIN_TIPS[Math.floor(Math.random() * ADMIN_TIPS.length)];
  protected readonly gymName = signal('');
  protected readonly gymLoaded = signal(false);
  protected readonly blocks = signal<GymBlock[]>([]);
  protected readonly plans = signal<GymPlan[]>([]);
  protected readonly members = signal<Member[]>([]);
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
  protected readonly themeContrast = computed(() => {
    const hex = this.themeColor();
    const match = PALETTES.find((p) => p.hex.toLowerCase() === hex.toLowerCase());
    return match?.contrast ?? computeContrast(hex);
  });
  protected readonly themeSurface = computed(() => deriveSurfaceTint(this.themeColor()));
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
    });
  }

  ngOnDestroy(): void {
    const root = document.documentElement.style;
    for (const property of THEMED_ROOT_PROPERTIES) {
      root.removeProperty(property);
    }
  }

  protected setSection(section: Section): void {
    this.section.set(section);
  }

  protected dayLabel(day: DayOfWeek): string {
    return DAYS.find((d) => d.value === day)?.label ?? day;
  }

  protected formatClp(value: number): string {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(value);
  }

  protected quotaLabel(plan: GymPlan): string {
    return plan.monthlyClasses === null ? 'Libre (ilimitado)' : `${plan.monthlyClasses} clases/mes`;
  }

  protected reshufflePalettes(): void {
    this.paletteOptions.set(randomSample(PALETTES, 4));
  }

  protected surfaceFor(palette: Palette): { bg: string; card: string } {
    return deriveSurfaceTint(palette.hex);
  }

  protected readonly themeSaving = signal(false);

  protected selectPalette(palette: Palette): void {
    this.selectColor(palette.hex);
  }

  protected onCustomColorInput(event: Event): void {
    const hex = (event.target as HTMLInputElement).value;
    this.selectColor(hex);
  }

  private selectColor(hex: string): void {
    if (this.themeSaving()) {
      return;
    }
    const previous = this.themeColor();
    this.themeColor.set(hex);
    this.themeSaving.set(true);
    this.gymService.updateMyTheme(hex).subscribe({
      next: () => this.themeSaving.set(false),
      error: () => {
        this.themeColor.set(previous);
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

  private async confirmAction(header: string, message: string): Promise<boolean> {
    const alert = await this.alertController.create({
      header,
      message,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        { text: 'Eliminar', role: 'destructive' },
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
        this.showToast(`Socio agregado: ${member.name}.`);
      },
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

  protected logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private loadGym(): void {
    this.gymService.getMine().subscribe({
      next: (gym) => {
        this.gymName.set(gym.name);
        this.logoSvg.set(gym.logoSvg);
        if (gym.themeColor) {
          this.themeColor.set(gym.themeColor);
        }
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
