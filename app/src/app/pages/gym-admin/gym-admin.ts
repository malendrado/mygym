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
  IonTitle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  businessOutline,
  calendarOutline,
  cloudUploadOutline,
  colorPaletteOutline,
  createOutline,
  peopleOutline,
  personAddOutline,
  personCircleOutline,
  refreshOutline,
  timeOutline,
  trashOutline,
} from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { MemberService } from '../../core/services/member.service';
import { CreateGymBlockRequest, DayOfWeek, GymBlock, UpdateGymBlockRequest, sortBlocksBySchedule } from '../../core/models/gym.model';
import { Member } from '../../core/models/member.model';
import { BloqueFormModal, DAYS } from '../admin/gyms/bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../admin/gyms/bloque-series-modal/bloque-series-modal';
import { deriveSurfaceTint } from '../../core/utils/gym-theme';

addIcons({
  'time-outline': timeOutline,
  'calendar-outline': calendarOutline,
  'people-outline': peopleOutline,
  'person-add-outline': personAddOutline,
  'person-circle-outline': personCircleOutline,
  'color-palette-outline': colorPaletteOutline,
  'refresh-outline': refreshOutline,
  'business-outline': businessOutline,
  'cloud-upload-outline': cloudUploadOutline,
  'create-outline': createOutline,
  'trash-outline': trashOutline,
});

type Status = 'idle' | 'loading' | 'saving' | 'error';
type Section = 'blocks' | 'members' | 'branding';

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

const THEMED_ROOT_PROPERTIES = [
  '--ion-color-primary',
  '--ion-color-primary-contrast',
  '--brand-accent',
  '--brand-accent-contrast',
  '--gym-panel-bg',
  '--gym-panel-card',
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
    BloqueFormModal,
    BloqueSeriesModal,
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
  protected readonly gymName = signal('');
  protected readonly gymLoaded = signal(false);
  protected readonly blocks = signal<GymBlock[]>([]);
  protected readonly members = signal<Member[]>([]);
  protected readonly isModalOpen = signal(false);
  protected readonly editingBlock = signal<GymBlock | null>(null);
  protected readonly isSeriesModalOpen = signal(false);
  protected readonly seriesCreating = signal(false);

  protected readonly logoSvg = signal<string | null>(null);
  protected readonly isRasterLogo = computed(() => (this.logoSvg() ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.logoSvg();
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });
  protected readonly logoUploading = signal(false);

  protected readonly themeColor = signal<string>(PALETTES[0].hex);
  protected readonly themeContrast = computed(() => {
    const match = PALETTES.find((p) => p.hex.toLowerCase() === this.themeColor().toLowerCase());
    return match?.contrast ?? PALETTES[0].contrast;
  });
  protected readonly themeSurface = computed(() => deriveSurfaceTint(this.themeColor()));
  protected readonly paletteOptions = signal<Palette[]>(randomSample(PALETTES, 4));

  protected readonly memberForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  constructor() {
    this.loadGym();
    this.loadBlocks();
    this.loadMembers();

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

  protected reshufflePalettes(): void {
    this.paletteOptions.set(randomSample(PALETTES, 4));
  }

  protected surfaceFor(palette: Palette): { bg: string; card: string } {
    return deriveSurfaceTint(palette.hex);
  }

  protected readonly themeSaving = signal(false);

  protected selectPalette(palette: Palette): void {
    if (this.themeSaving()) {
      return;
    }
    const previous = this.themeColor();
    this.themeColor.set(palette.hex);
    this.themeSaving.set(true);
    this.gymService.updateMyTheme(palette.hex).subscribe({
      next: () => this.themeSaving.set(false),
      error: () => {
        this.themeColor.set(previous);
        this.themeSaving.set(false);
        this.showToast('No pudimos guardar el color. Intenta nuevamente.', 'danger');
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
        this.gymLoaded.set(true);
      },
      error: () => {
        this.status.set('error');
        this.gymLoaded.set(true);
      },
    });
  }

  private loadBlocks(): void {
    this.gymService.listMyBlocks().subscribe({
      next: (blocks) => this.blocks.set(sortBlocksBySchedule(blocks)),
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
