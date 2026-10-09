/**
 * Saca el código de asistencia de lo que lee el escáner. El QR de la TV codifica un link
 * `https://<dominio>/checkin/<código>` (ver tv-screen.ts) — acá se acepta ese link o, por si
 * acaso, el código suelto. null = ese QR no es de asistencia de mygym. El backend igual valida el
 * código (vigencia, gimnasio, reserva del socio), esto solo evita llamarlo con basura.
 */
export function extractCheckinCode(raw: string): string | null {
  const text = raw.trim();
  if (!text) {
    return null;
  }
  try {
    const url = new URL(text);
    const match = url.pathname.match(/^\/checkin\/([A-Za-z0-9_-]{4,64})\/?$/);
    return match ? match[1] : null;
  } catch {
    // No es una URL: puede ser el código a secas.
    return /^[A-Za-z0-9_-]{4,64}$/.test(text) ? text : null;
  }
}
