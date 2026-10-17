import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';

/**
 * Igual que Validators.email, pero ignora espacios al principio y al final: quien pega "x@y.cl " desde el celular no debe ver
 * el botón deshabilitado por un espacio invisible (los formularios ya hacen trim() al enviar).
 */
export function trimmedEmail(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : control.value;
  return Validators.email({ value } as AbstractControl);
}
