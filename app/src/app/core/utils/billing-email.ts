/**
 * Correo de la factura: mismas reglas que el servidor (GymSubscriptionService.BILLING_EMAIL). Un solo @, dominio con punto y
 * terminación de 2 letras o más, sin puntos seguidos. Se normaliza a minúsculas y sin espacios en los bordes.
 */
const BILLING_EMAIL = /^(?!.*\.\.)[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$/;

export function normalizeBillingEmail(value: string): string {
  return (value ?? '').trim().toLowerCase();
}

export function isValidBillingEmail(value: string): boolean {
  return BILLING_EMAIL.test(normalizeBillingEmail(value));
}
