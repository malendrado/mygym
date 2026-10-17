import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { IonButton, IonButtons, IonContent, IonFooter, IonHeader, IonIcon, IonInput, IonItem, IonNote, IonSpinner, IonTitle, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { idCardOutline } from 'ionicons/icons';
import { MemberProfile } from '../../models/member.model';
import { formatRut, isValidRut, rutFormatValidator } from '../../utils/rut';
import {
  addressValidator,
  birthDateError,
  birthDateValidator,
  emergencyNameValidator,
  MAX_EMERGENCY_NAME_LENGTH,
  formatMobile,
  mobileValidator,
  MAX_ADDRESS_LENGTH,
} from '../../utils/member-profile';

addIcons({ 'id-card-outline': idCardOutline });

type FieldName = 'rut' | 'address' | 'birthDate' | 'phone' | 'emergencyContactName' | 'emergencyContactPhone';

function todayIsoDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

/**
 * Datos del socio: RUT, dirección, fecha de nacimiento, celular y contacto de emergencia. Lo usan:
 *  - el propio socio, como pantalla OBLIGATORIA al entrar a /member si le faltan datos
 *    (`required`: no se puede saltar ni cerrar; solo hay un botón "Salir" que emite `dismiss` y el socio cierra sesión);
 *  - el admin del gym y el super-admin, para cargar o corregir los datos de un socio.
 * Solo emite el perfil ya validado; nunca llama a la API (mismo patrón que ClosureModal).
 * El servidor revalida todo igual.
 */
@Component({
  selector: 'app-member-profile-modal',
  imports: [ReactiveFormsModule, IonHeader, IonFooter, IonToolbar, IonTitle, IonButtons, IonButton, IonContent, IonIcon, IonInput, IonItem, IonNote, IonSpinner],
  templateUrl: './member-profile-modal.html',
  styleUrl: './member-profile-modal.scss',
})
export class MemberProfileModal {
  @Input() title = 'Tus datos';
  @Input() intro = '';
  @Input() submitLabel = 'Guardar mis datos';
  /** true = pantalla obligatoria (en vez de "Cerrar" muestra "Salir"). */
  @Input() required = false;

  @Input() set initial(
    value: {
      rut?: string | null;
      address?: string | null;
      birthDate?: string | null;
      phone?: string | null;
      emergencyContactName?: string | null;
      emergencyContactPhone?: string | null;
    } | null,
  ) {
    this.form.reset({
      rut: value?.rut ?? '',
      address: value?.address ?? '',
      birthDate: value?.birthDate ?? '',
      phone: value?.phone ? formatMobile(value.phone) : '',
      emergencyContactName: value?.emergencyContactName ?? '',
      emergencyContactPhone: value?.emergencyContactPhone ? formatMobile(value.emergencyContactPhone) : '',
    });
  }

  @Input() set saving(value: boolean) {
    this._saving.set(value);
  }

  /** Mensaje del servidor si rechazó el guardado (el padre lo pasa, ver member.ts). */
  @Input() set serverError(value: string | null) {
    this._serverError.set(value);
  }

  @Output() saved = new EventEmitter<MemberProfile>();
  @Output() dismiss = new EventEmitter<void>();

  protected readonly maxBirthDate = todayIsoDate();
  protected readonly maxAddress = MAX_ADDRESS_LENGTH;
  protected readonly maxEmergencyName = MAX_EMERGENCY_NAME_LENGTH;
  private readonly _saving = signal(false);
  private readonly _serverError = signal<string | null>(null);
  protected readonly isSaving = this._saving.asReadonly();
  protected readonly serverErrorText = this._serverError.asReadonly();

  protected readonly form = new FormGroup({
    rut: new FormControl('', { nonNullable: true, validators: [Validators.required, rutFormatValidator()] }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.required, addressValidator()] }),
    birthDate: new FormControl('', { nonNullable: true, validators: [Validators.required, birthDateValidator()] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.required, mobileValidator()] }),
    emergencyContactName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, emergencyNameValidator()],
    }),
    emergencyContactPhone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, mobileValidator()],
    }),
  });

  protected showError(name: FieldName): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected errorText(name: FieldName): string {
    const c = this.form.controls[name];
    switch (name) {
      case 'rut':
        return c.hasError('required') ? 'Ingresa tu RUT.' : 'RUT inválido. Revisa el número y el dígito verificador.';
      case 'address':
        return c.hasError('required') ? 'Ingresa tu dirección.' : String(c.getError('address'));
      case 'birthDate':
        return c.hasError('required') ? 'Ingresa tu fecha de nacimiento.' : String(c.getError('birthDate'));
      case 'emergencyContactName':
        return c.hasError('required')
          ? 'Ingresa el nombre de tu contacto de emergencia.'
          : String(c.getError('emergencyName'));
      case 'emergencyContactPhone':
        return c.hasError('required')
          ? 'Ingresa el celular de tu contacto de emergencia.'
          : 'Ingresa un celular chileno válido, por ejemplo +56 9 1234 5678.';
      default:
        return c.hasError('required') ? 'Ingresa tu celular.' : 'Ingresa un celular chileno válido, por ejemplo +56 9 1234 5678.';
    }
  }

  // Al salir de cada campo se deja con su formato final (RUT con puntos y guión, celular con espacios).
  protected tidyRut(): void {
    const c = this.form.controls.rut;
    if (isValidRut(c.value)) {
      c.setValue(formatRut(c.value));
    }
  }

  protected tidyPhone(name: 'phone' | 'emergencyContactPhone' = 'phone'): void {
    const c = this.form.controls[name];
    if (!c.hasError('mobile')) {
      c.setValue(formatMobile(c.value));
    }
  }

  protected birthHint(): string | null {
    const value = this.form.controls.birthDate.value;
    return value ? birthDateError(value) : null;
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.tidyRut();
    this.tidyPhone();
    this.tidyPhone('emergencyContactPhone');
    if (this.form.invalid || this._saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    this.saved.emit({
      rut: raw.rut.trim(),
      address: raw.address.trim().replace(/\s+/g, ' '),
      birthDate: raw.birthDate,
      phone: raw.phone.trim(),
      emergencyContactName: raw.emergencyContactName.trim().replace(/\s+/g, ' '),
      emergencyContactPhone: raw.emergencyContactPhone.trim(),
    });
  }

  protected cancel(): void {
    this.dismiss.emit();
  }
}
