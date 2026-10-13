import { Component, HostListener, Input, signal } from '@angular/core';
import { BUILD_INFO } from '../../../../environments/build-info';

/**
 * Número de versión del build que corre, para comprobar a ojo si un despliegue ya llegó a la app
 * instalada o a la TV (el service worker a veces tarda dos aperturas). Lo escribe deploy.sh; nunca
 * se edita a mano.
 *
 * - `subtle` (app del socio): solo `v<commit>`, letra mínima y casi transparente; al tocarlo se
 *   alterna con la versión completa (fecha incluida).
 * - `inline` (paneles y landing): versión completa, tenue.
 * - `tv`: fija en la esquina inferior izquierda, apenas visible a distancia; agrega la resolucion real del
 *   navegador de la TV (para ajustar el diseño a la pantalla de cada gimnasio sin ir a medirla).
 */
@Component({
  selector: 'app-version-tag',
  templateUrl: './version-tag.html',
  styleUrl: './version-tag.scss',
})
export class VersionTag {
  @Input() variant: 'subtle' | 'inline' | 'tv' = 'inline';

  protected readonly expanded = signal(false);

  private readonly commit = BUILD_INFO.commit + (BUILD_INFO.dirty ? '-dirty' : '');

  // Resolucion en pixeles CSS del area visible (en pantalla completa = la de la TV) y, si el navegador escala (TV 4K que
  // reporta 1920x1080), la fisica entre parentesis.
  protected readonly resolution = signal(VersionTag.readResolution());

  protected readonly short = `v${this.commit}`;
  protected readonly full = `v${BUILD_INFO.date} · ${this.commit}`;

  @HostListener('window:resize')
  protected onResize(): void {
    this.resolution.set(VersionTag.readResolution());
  }

  private static readResolution(): string {
    const view = `${window.innerWidth}×${window.innerHeight}`;
    const dpr = window.devicePixelRatio || 1;
    return dpr === 1 ? view : `${view} @${Math.round(dpr * 100) / 100}x`;
  }

  protected toggle(): void {
    if (this.variant === 'subtle') {
      this.expanded.update((value) => !value);
    }
  }
}
