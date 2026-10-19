import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  AlertController,
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonSpinner,
  IonText,
  IonTextarea,
  IonTitle,
  IonToggle,
  IonToolbar,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { addOutline, createOutline, trashOutline } from 'ionicons/icons';
import { MygymPlan, MygymPlanRequest } from '../../../../core/models/subscription.model';
import { SubscriptionService } from '../../../../core/services/subscription.service';
import { formatClp } from '../../../../core/components/gym-subscription/gym-subscription';

addIcons({ 'add-outline': addOutline, 'create-outline': createOutline, 'trash-outline': trashOutline });

type Status = 'loading' | 'loaded' | 'error';

function toInt(value: unknown): number | null {
  const n = parseInt(String(value ?? '').trim(), 10);
  return Number.isFinite(n) && n > 0 ? n : null;
}

/**
 * Planes que mygym le vende a los gimnasios. De aquí salen la landing y el FAQ, y el plan que se asigna a cada gimnasio. Un plan
 * que ya tiene gimnasios no se borra: se desactiva (deja de verse en la landing y de poder asignarse).
 */
@Component({
  selector: 'app-mygym-plans',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonBackButton, IonContent, IonIcon, IonInput, IonSpinner, IonText, IonTextarea, IonToggle],
  templateUrl: './mygym-plans.html',
  styleUrl: './mygym-plans.scss',
})
export class MygymPlans implements OnInit {
  private readonly service = inject(SubscriptionService);
  private readonly toastController = inject(ToastController);
  private readonly alertController = inject(AlertController);

  protected readonly status = signal<Status>('loading');
  protected readonly plans = signal<MygymPlan[]>([]);
  protected readonly saving = signal(false);

  /** null = no se está editando; 0 = plan nuevo; otro = id del plan. */
  protected readonly editingId = signal<number | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly clientsLabel = signal('');
  protected readonly price = signal('');
  protected readonly maxUsers = signal('');
  protected readonly maxProfesores = signal('10');
  protected readonly maxAdmins = signal('5');
  protected readonly features = signal('');
  protected readonly highlighted = signal(false);
  protected readonly sortOrder = signal('0');
  protected readonly active = signal(true);

  protected readonly isNew = computed(() => this.editingId() === 0);
  protected readonly clp = formatClp;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.service.listPlans().subscribe({
      next: (plans) => {
        this.plans.set(plans);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  protected startNew(): void {
    this.fill(null);
    this.editingId.set(0);
  }

  protected startEdit(plan: MygymPlan): void {
    this.fill(plan);
    this.editingId.set(plan.id);
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.formError.set(null);
  }

  protected save(): void {
    const name = this.name().trim();
    const label = this.clientsLabel().trim();
    const code = (this.isNew() ? this.code() : (this.plans().find((p) => p.id === this.editingId())?.code ?? '')).trim();
    if (!name || !label || !code) {
      this.formError.set('Completa el código, el nombre y el texto de socios (ej. «Hasta 100 socios»).');
      return;
    }
    const profesores = toInt(this.maxProfesores());
    const admins = toInt(this.maxAdmins());
    if (profesores === null || admins === null) {
      this.formError.set('Los máximos de profesores y administradores deben ser números mayores que cero.');
      return;
    }
    this.formError.set(null);
    const request: MygymPlanRequest & { code: string } = {
      code,
      name,
      clientsLabel: label,
      priceClp: toInt(this.price()),
      maxUsers: toInt(this.maxUsers()),
      maxProfesores: profesores,
      maxAdmins: admins,
      features: this.features()
        .split('\n')
        .map((f) => f.trim())
        .filter((f) => f.length > 0),
      highlighted: this.highlighted(),
      sortOrder: Math.max(0, parseInt(this.sortOrder(), 10) || 0),
      active: this.active(),
    };
    this.saving.set(true);
    const call = this.isNew() ? this.service.createPlan(request) : this.service.updatePlan(this.editingId()!, request);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editingId.set(null);
        void this.toast('Plan guardado. La landing se actualiza en unos minutos.');
        this.load();
      },
      error: (err: Error) => {
        this.saving.set(false);
        this.formError.set(err.message || 'No pudimos guardar el plan.');
      },
    });
  }

  protected async remove(plan: MygymPlan): Promise<void> {
    const alert = await this.alertController.create({
      header: `¿Borrar el plan ${plan.name}?`,
      message: 'Si hay gimnasios con este plan no se puede borrar: desactívalo en vez de borrarlo.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Borrar',
          role: 'destructive',
          handler: () =>
            this.service.deletePlan(plan.id).subscribe({
              next: () => {
                void this.toast('Plan borrado.');
                this.load();
              },
              error: (err: Error) => void this.toast(err.message || 'No pudimos borrar el plan.', 'danger'),
            }),
        },
      ],
    });
    await alert.present();
  }

  protected text(event: Event): string {
    return String((event as CustomEvent).detail?.value ?? '');
  }

  private fill(plan: MygymPlan | null): void {
    this.code.set(plan?.code ?? '');
    this.name.set(plan?.name ?? '');
    this.clientsLabel.set(plan?.clientsLabel ?? '');
    this.price.set(plan?.priceClp?.toString() ?? '');
    this.maxUsers.set(plan?.maxUsers?.toString() ?? '');
    this.maxProfesores.set((plan?.maxProfesores ?? 10).toString());
    this.maxAdmins.set((plan?.maxAdmins ?? 5).toString());
    this.features.set((plan?.features ?? ['Todas las funciones incluidas']).join('\n'));
    this.highlighted.set(plan?.highlighted ?? false);
    this.sortOrder.set((plan?.sortOrder ?? (this.plans().length + 1)).toString());
    this.active.set(plan?.active ?? true);
    this.formError.set(null);
  }

  private async toast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4000, position: 'bottom', color });
    await toast.present();
  }
}
