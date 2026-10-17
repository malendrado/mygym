import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IonButton } from '@ionic/angular';

/**
 * Bloque de error con "Reintentar": para cuando una carga falla y no hay datos que mostrar. Evita que la
 * pantalla quede en blanco o, peor, muestre un estado vacío inventado ("sin plan", "no hay clases").
 */
@Component({
  selector: 'app-error-state',
  imports: [IonButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-state" role="alert">
      <p class="error-state__title">{{ title() }}</p>
      @if (message()) {
        <p class="error-state__message">{{ message() }}</p>
      }
      <ion-button size="small" fill="outline" class="error-state__retry" (click)="retry.emit()">
        {{ buttonLabel() }}
      </ion-button>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.4rem;
      padding: 1.5rem 1rem;
      border: 1px solid color-mix(in srgb, var(--ion-color-danger, #eb445a) 40%, transparent);
      border-radius: 12px;
      background: color-mix(in srgb, var(--ion-color-danger, #eb445a) 8%, transparent);
      text-align: center;
    }

    .error-state__title {
      margin: 0;
      color: var(--brand-text, #eaf6f7);
      font-weight: 700;
      font-size: 0.95rem;
    }

    .error-state__message {
      margin: 0;
      color: var(--brand-text-dim, #a9c2c6);
      font-size: 0.82rem;
      line-height: 1.35;
    }

    /* 44px de piso táctil aunque el botón sea "small". */
    .error-state__retry {
      height: 44px;
      margin: 0.3rem 0 0;
    }
  `,
})
export class ErrorState {
  readonly title = input.required<string>();
  readonly message = input('');
  readonly buttonLabel = input('Reintentar');
  readonly retry = output<void>();
}
