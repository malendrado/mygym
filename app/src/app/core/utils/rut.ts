import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** RUT chileno — el algoritmo de dígito verificador (módulo 11) es EL MISMO para persona
 *  natural y persona jurídica (empresa); no hay dos validaciones distintas, solo un único
 *  cálculo que cubre ambos casos. */

/** Deja solo dígitos y K/k, sin puntos/guión/espacios — para validar o recalcular. */
export function cleanRut(value: string): string {
  return (value ?? '').replace(/[^0-9kK]/g, '').toUpperCase();
}

function computeDv(body: string): string {
  let sum = 0;
  let multiplier = 2;
  for (let i = body.length - 1; i >= 0; i--) {
    sum += parseInt(body[i], 10) * multiplier;
    multiplier = multiplier === 7 ? 2 : multiplier + 1;
  }
  const remainder = 11 - (sum % 11);
  if (remainder === 11) return '0';
  if (remainder === 10) return 'K';
  return String(remainder);
}

/** true si el RUT (con o sin puntos/guión) es válido — formato y dígito verificador. */
export function isValidRut(value: string): boolean {
  const clean = cleanRut(value);
  if (!/^\d{1,8}[0-9K]$/.test(clean)) {
    return false;
  }
  const body = clean.slice(0, -1);
  const dv = clean.slice(-1);
  return computeDv(body) === dv;
}

/** Formatea a "XX.XXX.XXX-D" — si el RUT no es válido, devuelve el valor tal cual (no fuerza
 *  un formato sobre algo que el usuario todavía está escribiendo o que quedó mal tipeado). */
export function formatRut(value: string): string {
  const clean = cleanRut(value);
  if (!isValidRut(clean)) {
    return value;
  }
  const body = clean.slice(0, -1);
  const dv = clean.slice(-1);
  const withDots = body.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${withDots}-${dv}`;
}

/** Validator reactivo — solo exige formato válido si el campo tiene algo escrito (no reemplaza
 *  a Validators.required, que se agrega aparte donde el campo sea obligatorio). */
export function rutFormatValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (!value || !String(value).trim()) {
      return null;
    }
    return isValidRut(value) ? null : { rut: true };
  };
}
