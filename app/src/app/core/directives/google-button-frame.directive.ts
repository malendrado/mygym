import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * Marco del botón "Iniciar sesión con Google": le pone la clase `is-ready` cuando el iframe de Google ya cargó y pintó su
 * estado final.
 *
 * Por qué hace falta: el botón real es un iframe que aparece 1-2 s después de abrir la página y pasa por varios estados
 * (vacío 0x0, una píldora gris mientras carga, blanca ya lista, y un salto de ancho/alto). Con solo `:has(iframe)` el aro de
 * color se encendía alrededor de un iframe todavía vacío o gris, y se veía como un parpadeo. Ahora el marco muestra un
 * esqueleto neutro y el aro y el botón entran juntos, ya listos (ver .google-auth__frame en login.scss y join.scss).
 */
@Directive({ selector: '.google-auth__frame', standalone: true })
export class GoogleButtonFrameDirective implements OnInit, OnDestroy {
  // Tiempo de espera máximo desde que aparece el iframe: si por algún motivo nunca avisa su carga, se muestra igual.
  private static readonly FALLBACK_MS = 2500;
  // Margen tras el evento load para que Google termine de pintar la píldora blanca.
  private static readonly SETTLE_MS = 150;
  // Un iframe de Google ya listo mide >100 px de ancho; antes mide 0 o unos pocos píxeles.
  private static readonly MIN_READY_WIDTH = 100;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private observer: MutationObserver | null = null;
  private watching = false;
  private destroyed = false;
  private timers: ReturnType<typeof setTimeout>[] = [];

  ngOnInit(): void {
    const el = this.host.nativeElement;
    if (typeof MutationObserver === 'undefined') {
      el.classList.add('is-ready');
      return;
    }
    this.observer = new MutationObserver(() => this.watchIframe());
    this.observer.observe(el, { childList: true, subtree: true });
    this.watchIframe();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.observer?.disconnect();
    this.timers.forEach(clearTimeout);
  }

  private watchIframe(): void {
    const el = this.host.nativeElement;
    const iframe = el.querySelector('iframe');
    if (!iframe || this.watching) {
      return;
    }
    this.watching = true;
    const reveal = () => {
      if (el.classList.contains('is-ready')) {
        return;
      }
      // Dos fotogramas: el aro y el botón entran sobre un iframe ya pintado, no sobre uno recién insertado.
      requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('is-ready')));
      this.observer?.disconnect();
    };
    const revealWhenSized = () => {
      if (this.destroyed || el.classList.contains('is-ready')) {
        return;
      }
      if (iframe.getBoundingClientRect().width >= GoogleButtonFrameDirective.MIN_READY_WIDTH) {
        reveal();
      } else {
        this.timers.push(setTimeout(revealWhenSized, GoogleButtonFrameDirective.SETTLE_MS));
      }
    };
    iframe.addEventListener(
      'load',
      () => {
        if (!this.destroyed) {
          this.timers.push(setTimeout(revealWhenSized, GoogleButtonFrameDirective.SETTLE_MS));
        }
      },
      { once: true },
    );
    this.timers.push(setTimeout(reveal, GoogleButtonFrameDirective.FALLBACK_MS));
  }
}
