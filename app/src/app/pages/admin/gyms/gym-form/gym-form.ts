import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
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
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonNote,
  IonText,
  IonTextarea,
  IonTitle,
  IonToggle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { businessOutline, calendarOutline, peopleOutline, settingsOutline, sparklesOutline, timeOutline } from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { GymService } from '../../../../core/services/gym.service';
import {
  Admin,
  BrandingSuggestion,
  CreateGymBlockRequest,
  Gym,
  GymBlock,
  UpdateGymBlockRequest,
  sortBlocksBySchedule,
} from '../../../../core/models/gym.model';
import { BloqueFormModal } from '../bloque-form-modal/bloque-form-modal';
import { BloqueSeriesModal } from '../bloque-series-modal/bloque-series-modal';
import { QuantityStepper } from '../../../../core/components/quantity-stepper/quantity-stepper';

addIcons({
  'business-outline': businessOutline,
  'settings-outline': settingsOutline,
  'time-outline': timeOutline,
  'calendar-outline': calendarOutline,
  'people-outline': peopleOutline,
  'sparkles-outline': sparklesOutline,
});

type Status = 'idle' | 'loading' | 'saving' | 'error';
type SuggestStatus = 'idle' | 'loading' | 'error';

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
    IonModal,
    BloqueFormModal,
    BloqueSeriesModal,
    QuantityStepper,
  ],
  templateUrl: './gym-form.html',
  styleUrl: './gym-form.scss',
})
export class GymForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gymService = inject(GymService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);

  protected readonly status = signal<Status>('idle');
  protected readonly gymId = signal<number | null>(null);
  protected readonly blocks = signal<GymBlock[]>([]);
  protected readonly isModalOpen = signal(false);
  protected readonly editingBlock = signal<GymBlock | null>(null);
  protected readonly isSeriesModalOpen = signal(false);
  protected readonly seriesCreating = signal(false);
  protected readonly admins = signal<Admin[]>([]);
  protected readonly adminError = signal<string | null>(null);

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

  protected readonly gymName = signal('');
  protected readonly gymSlug = signal('');

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.gymId.set(id);
      this.loadGym(id);
      this.loadBlocks(id);
      this.loadAdmins(id);
    }
  }

  protected get isEditing(): boolean {
    return this.gymId() !== null;
  }

  private loadGym(id: number): void {
    this.status.set('loading');
    this.gymService.get(id).subscribe({
      next: (gym) => {
        this.gymName.set(gym.name);
        this.gymSlug.set(gym.slug);
        this.configForm.setValue({
          active: gym.active,
          maxUsers: gym.maxUsers,
          googleLoginEnabled: gym.googleLoginEnabled,
          logoSvg: gym.logoSvg ?? '',
        });
        this.status.set('idle');
      },
      error: () => this.status.set('error'),
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
      next: (gym: Gym) => this.router.navigate(['/admin/gyms', gym.id]),
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
      next: () => this.status.set('idle'),
      error: () => this.status.set('error'),
    });
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

  private async showToast(message: string): Promise<void> {
    const toast = await this.toastController.create({
      message,
      duration: 4000,
      position: 'bottom',
      color: 'success',
    });
    await toast.present();
  }
}
