import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular';
import { MygymSettings, MygymSettingsRequest } from '../../../../core/models/mygym-settings.model';
import { MygymSettingsService } from '../../../../core/services/mygym-settings.service';
import { isValidRut } from '../../../../core/utils/rut';

type Status = 'loading' | 'loaded' | 'error';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Datos de mygym como empresa: contacto, empresa que firma el contrato y cuenta para transferencias. Es la única fuente: la API, el
 * contrato, los correos y toda la app leen de aquí. Un campo vacío de la cuenta bancaria vuelve al valor de respaldo del servidor.
 */
@Component({
  selector: 'app-mygym-settings',
  imports: [IonHeader, IonToolbar, IonTitle, IonButtons, IonBackButton, IonContent, IonInput, IonButton, IonSpinner, IonText],
  templateUrl: './mygym-settings.html',
  styleUrl: './mygym-settings.scss',
})
export class MygymSettingsPage implements OnInit {
  private readonly service = inject(MygymSettingsService);
  private readonly toastController = inject(ToastController);

  protected readonly status = signal<Status>('loading');
  protected readonly saving = signal(false);
  protected readonly tried = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly updatedAt = signal<string | null>(null);

  protected readonly whatsapp = signal('');
  protected readonly contactEmail = signal('');
  protected readonly companyName = signal('');
  protected readonly companyRut = signal('');
  protected readonly legalRepName = signal('');
  protected readonly legalRepRut = signal('');
  protected readonly companyAddress = signal('');
  protected readonly bankHolder = signal('');
  protected readonly bankRut = signal('');
  protected readonly bankName = signal('');
  protected readonly bankAccountType = signal('');
  protected readonly bankAccountNumber = signal('');
  protected readonly bankEmail = signal('');

  /** Mensaje por campo; vacío = sin error. */
  protected readonly errors = computed(() => ({
    whatsapp: /^[0-9]{8,15}$/.test(this.whatsapp().trim()) ? '' : 'Solo dígitos con código de país, sin + ni espacios (ej: 56912345678).',
    contactEmail: EMAIL.test(this.contactEmail().trim()) ? '' : 'Escribe un correo válido.',
    companyName: this.companyName().trim() ? '' : 'Obligatorio.',
    companyRut: isValidRut(this.companyRut()) ? '' : 'RUT no válido: revisa el dígito verificador.',
    legalRepName: this.legalRepName().trim() ? '' : 'Obligatorio.',
    legalRepRut: isValidRut(this.legalRepRut()) ? '' : 'RUT no válido: revisa el dígito verificador.',
    companyAddress: this.companyAddress().trim() ? '' : 'Obligatorio.',
    bankRut: !this.bankRut().trim() || isValidRut(this.bankRut()) ? '' : 'RUT no válido: revisa el dígito verificador.',
    bankEmail: !this.bankEmail().trim() || EMAIL.test(this.bankEmail().trim()) ? '' : 'Escribe un correo válido.',
  }));

  protected readonly updatedLabel = computed(() => {
    const iso = this.updatedAt();
    return iso ? new Date(iso).toLocaleDateString('es-CL', { day: 'numeric', month: 'long', year: 'numeric' }) : null;
  });

  protected readonly hasErrors = computed(() => Object.values(this.errors()).some((e) => e !== ''));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.service.get().subscribe({
      next: (settings) => {
        this.fill(settings);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  protected save(): void {
    this.tried.set(true);
    this.saveError.set(null);
    if (this.hasErrors()) {
      this.saveError.set('Revisa los campos marcados.');
      return;
    }
    const request: MygymSettingsRequest = {
      whatsapp: this.whatsapp().trim(),
      contactEmail: this.contactEmail().trim(),
      companyName: this.companyName().trim(),
      companyRut: this.companyRut().trim(),
      legalRepName: this.legalRepName().trim(),
      legalRepRut: this.legalRepRut().trim(),
      companyAddress: this.companyAddress().trim(),
      bankHolder: this.bankHolder().trim(),
      bankRut: this.bankRut().trim(),
      bankName: this.bankName().trim(),
      bankAccountType: this.bankAccountType().trim(),
      bankAccountNumber: this.bankAccountNumber().trim(),
      bankEmail: this.bankEmail().trim(),
    };
    this.saving.set(true);
    this.service.save(request).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.tried.set(false);
        this.fill(saved);
        void this.toast('Datos de mygym guardados. Se aplican en toda la app en un par de minutos.');
      },
      error: (err: Error) => {
        this.saving.set(false);
        this.saveError.set(err.message || 'No pudimos guardar los datos.');
      },
    });
  }

  protected text(event: Event): string {
    return String((event as CustomEvent).detail?.value ?? '');
  }

  private fill(s: MygymSettings): void {
    this.whatsapp.set(s.whatsapp ?? '');
    this.contactEmail.set(s.contactEmail ?? '');
    this.companyName.set(s.companyName ?? '');
    this.companyRut.set(s.companyRut ?? '');
    this.legalRepName.set(s.legalRepName ?? '');
    this.legalRepRut.set(s.legalRepRut ?? '');
    this.companyAddress.set(s.companyAddress ?? '');
    this.bankHolder.set(s.bankHolder ?? '');
    this.bankRut.set(s.bankRut ?? '');
    this.bankName.set(s.bankName ?? '');
    this.bankAccountType.set(s.bankAccountType ?? '');
    this.bankAccountNumber.set(s.bankAccountNumber ?? '');
    this.bankEmail.set(s.bankEmail ?? '');
    this.updatedAt.set(s.updatedAt);
  }

  private async toast(message: string): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4000, position: 'bottom', color: 'success' });
    await toast.present();
  }
}
