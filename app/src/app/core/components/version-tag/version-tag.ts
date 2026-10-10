import { Component, Input, signal } from '@angular/core';
import { BUILD_INFO } from '../../../../environments/build-info';

/**
 * Número de versión del build que corre, para comprobar a ojo si un despliegue ya llegó a la app
 * instalada o a la TV (el service worker a veces tarda dos aperturas). Lo escribe deploy.sh; nunca
 * se edita a mano.
 *
 * - `subtle` (app del socio): solo `v<commit>`, letra mínima y casi transparente; al tocarlo se
 *   alterna con la versión completa (fecha incluida).
 * - `inline` (paneles y landing): versión completa, tenue.
 * - `tv`: fija en la esquina inferior izquierda, apenas visible a distancia.
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

  protected readonly short = `v${this.commit}`;
  protected readonly full = `v${BUILD_INFO.date} · ${this.commit}`;

  protected toggle(): void {
    if (this.variant === 'subtle') {
      this.expanded.update((value) => !value);
    }
  }
}
