import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';

/**
 * Pantalla de carga con identidad para las esperas largas: el logo (el del gimnasio si se pasa, el de mygym si no) dentro de un anillo
 * que gira y una pesa que sube y baja. NO es para cada consulta: una lista que cambia en menos de ~1 s se queda como está (atenuada,
 * con la barra fina); esto aparece cuando la espera ya se nota (la carga inicial del panel o una consulta que pasa de ~1,2 s).
 *
 * Con `tips` rota frases cortas cada ~2,4 s para que una espera larga no parezca congelada. Respeta prefers-reduced-motion: la pesa se
 * queda quieta y el anillo sigue girando despacio (girar es lo que dice "estoy trabajando", ver styles.scss).
 */
@Component({
  selector: 'app-gym-loader',
  templateUrl: './gym-loader.html',
  styleUrl: './gym-loader.scss',
})
export class GymLoader implements OnInit {
  /** `src` ya seguro del logo del gimnasio (toLogoImgSrc); null = logo de mygym. */
  readonly logo = input<string | null>(null);
  readonly message = input('Cargando…');
  /** Frases que rotan bajo el mensaje; vacío = solo el mensaje. */
  readonly tips = input<readonly string[]>([]);
  /** Versión chica para ponerla encima de una lista (sin frases). */
  readonly compact = input(false);

  protected readonly tipIndex = signal(0);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    if (this.tips().length > 1) {
      const timer = setInterval(() => this.tipIndex.update((i) => (i + 1) % this.tips().length), 2400);
      this.destroyRef.onDestroy(() => clearInterval(timer));
    }
  }
}
