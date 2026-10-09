/**
 * Convierte un logo de gimnasio (SVG crudo pegado/subido por un admin, o ya un `data:image` URI
 * para un logo raster) en un `src` seguro para `<img>`.
 *
 * Antes este SVG se renderizaba con `[innerHTML]` + `DomSanitizer.bypassSecurityTrustHtml` (para
 * que el color/tema siguiera siendo CSS-aware) — eso desactiva por completo el sanitizador de
 * Angular, y el único filtro real era un denylist de substrings en el backend (`SvgSanitizer.java`)
 * que no cubría SMIL (`<animate onbegin="...">`) ni atributos `on*` arbitrarios: un admin
 * malicioso podía ejecutar JS en el navegador de sus propios socios, de cualquier visitante de
 * `/j/:slug` y hasta del super-admin al revisar el gimnasio (auditoría de seguridad 2026-10-09).
 *
 * Cargar el SVG como IMAGEN (no como documento inline) elimina la clase de vulnerabilidad entera:
 * un `<img>` nunca ejecuta `<script>`, manejadores `on*` ni SMIL embebidos en el SVG que carga,
 * sin importar qué tan débil sea el sanitizado del lado del servidor.
 */
export function toLogoImgSrc(logo: string | null | undefined): string | null {
  if (!logo) {
    return null;
  }
  if (logo.startsWith('data:image')) {
    return logo;
  }
  return `data:image/svg+xml;utf8,${encodeURIComponent(logo)}`;
}
