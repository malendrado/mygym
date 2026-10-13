import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Reglas de los datos de contacto del socio (celular, fecha de nacimiento, dirección). Réplica de
 * MemberProfileRules.java: el servidor valida igual y es la fuente de verdad. El RUT usa rut.ts.
 */

export const MIN_AGE_YEARS = 12;
export const MAX_AGE_YEARS = 100;
export const MIN_ADDRESS_LENGTH = 5;
export const MAX_ADDRESS_LENGTH = 200;
export const MIN_EMERGENCY_NAME_LENGTH = 2;
export const MAX_EMERGENCY_NAME_LENGTH = 100;

/** Celular chileno (sirve para WhatsApp) → "+56912345678", o null si no es válido. Acepta
 *  "9 1234 5678", "+56 9 1234 5678", "56912345678", con espacios, guiones o paréntesis. */
export function normalizeMobile(value: unknown): string | null {
  let digits = String(value ?? '').replace(/\D/g, '');
  if (digits.length === 11 && digits.startsWith('569')) {
    digits = digits.slice(2);
  }
  return digits.length === 9 && digits.startsWith('9') ? `+56${digits}` : null;
}

/** "+56912345678" → "+56 9 1234 5678" (si no es válido, devuelve el texto tal cual). */
export function formatMobile(value: string | null | undefined): string {
  const normalized = normalizeMobile(value);
  if (!normalized) {
    return value ?? '';
  }
  const d = normalized.slice(3); // 9 dígitos
  return `+56 ${d[0]} ${d.slice(1, 5)} ${d.slice(5)}`;
}

/** Link de WhatsApp (wa.me) al celular del socio, o null si no tiene uno válido. */
export function whatsappUrl(phone: string | null | undefined): string | null {
  const normalized = normalizeMobile(phone);
  return normalized ? `https://wa.me/${normalized.slice(1)}` : null;
}

function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
}

/** Mensaje de error de una fecha de nacimiento ISO "YYYY-MM-DD", o null si es válida. */
export function birthDateError(iso: string | null | undefined): string | null {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    return 'Ingresa tu fecha de nacimiento.';
  }
  const today = todayIso();
  if (iso > today) {
    return 'La fecha de nacimiento no puede ser futura.';
  }
  const [y, m, d] = iso.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  let age = ty - y;
  if (tm < m || (tm === m && td < d)) {
    age--;
  }
  return age < MIN_AGE_YEARS || age > MAX_AGE_YEARS
    ? `Revisa la fecha de nacimiento: la edad debe estar entre ${MIN_AGE_YEARS} y ${MAX_AGE_YEARS} años.`
    : null;
}

export function addressError(value: unknown): string | null {
  const address = String(value ?? '').trim().replace(/\s+/g, ' ');
  if (address.length < MIN_ADDRESS_LENGTH) {
    return 'Escribe tu dirección completa (calle, número y comuna).';
  }
  return address.length > MAX_ADDRESS_LENGTH ? `La dirección es demasiado larga (máximo ${MAX_ADDRESS_LENGTH} letras).` : null;
}

export function emergencyNameError(value: unknown): string | null {
  const name = String(value ?? '').trim().replace(/\s+/g, ' ');
  if (name.length < MIN_EMERGENCY_NAME_LENGTH) {
    return 'Escribe el nombre de tu contacto de emergencia.';
  }
  return name.length > MAX_EMERGENCY_NAME_LENGTH
    ? `El nombre es demasiado largo (máximo ${MAX_EMERGENCY_NAME_LENGTH} letras).`
    : null;
}

// Validators reactivos: solo juzgan si el campo tiene algo escrito (la obligatoriedad va aparte, con
// Validators.required, igual que rutFormatValidator).
export function mobileValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    !control.value || normalizeMobile(control.value) ? null : { mobile: true };
}

export function birthDateValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) {
      return null;
    }
    const error = birthDateError(control.value);
    return error ? { birthDate: error } : null;
  };
}

export function emergencyNameValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value || !String(control.value).trim()) {
      return null;
    }
    const error = emergencyNameError(control.value);
    return error ? { emergencyName: error } : null;
  };
}

export function addressValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value || !String(control.value).trim()) {
      return null;
    }
    const error = addressError(control.value);
    return error ? { address: error } : null;
  };
}

/** Fecha de Excel (número de serie o texto dd-mm-aaaa / aaaa-mm-dd / Date) → ISO "YYYY-MM-DD", o null. */
export function parseBirthDateCell(raw: unknown): string | null {
  const pad = (n: number): string => n.toString().padStart(2, '0');
  if (raw instanceof Date && !isNaN(raw.getTime())) {
    return `${raw.getUTCFullYear()}-${pad(raw.getUTCMonth() + 1)}-${pad(raw.getUTCDate())}`;
  }
  if (typeof raw === 'number' && raw > 1 && raw < 80000) {
    // Número de serie de Excel (días desde 1899-12-30).
    const date = new Date(Date.UTC(1899, 11, 30) + Math.round(raw) * 86_400_000);
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
  }
  const text = String(raw ?? '').trim();
  let match = text.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/);
  if (match) {
    return validIso(Number(match[3]), Number(match[2]), Number(match[1]));
  }
  match = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  return match ? validIso(Number(match[1]), Number(match[2]), Number(match[3])) : null;
}

function validIso(y: number, m: number, d: number): string | null {
  const date = new Date(Date.UTC(y, m - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) {
    return null;
  }
  return `${y}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}`;
}
