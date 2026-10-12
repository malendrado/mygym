const GENERIC_SERVER_MESSAGE = 'Ocurrió un error inesperado. Intenta nuevamente.';

/**
 * Texto de error para un toast. El interceptor ya convierte la respuesta del servidor en un Error con
 * su mensaje; pero NO todos sirven al usuario: un 404 o un 500 traen textos técnicos o genéricos.
 * Solo se muestra el mensaje del servidor cuando es una validación o un conflicto que el usuario puede
 * corregir (400, 409, 422) — "RUT inválido", "ese email ya tiene una cuenta"… — y en cualquier otro
 * caso, el texto propio de la pantalla.
 */
export function errorText(err: unknown, fallback: string): string {
  const e = err as { message?: string; status?: number } | null;
  const actionable = e?.status === 400 || e?.status === 409 || e?.status === 422;
  return actionable && e?.message && e.message !== GENERIC_SERVER_MESSAGE ? e.message : fallback;
}
