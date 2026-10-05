import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { IonButton, IonContent, IonHeader, IonIcon, IonInput, IonItem, IonSpinner, IonTitle, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { alertCircleOutline, eyeOffOutline, eyeOutline } from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { AuthTokenPurpose, homeRouteForRole } from '../../core/models/auth.model';

addIcons({ 'eye-outline': eyeOutline, 'eye-off-outline': eyeOffOutline, 'alert-circle-outline': alertCircleOutline });

// Mismo criterio que login.ts/join.ts: pausa deliberada para que el mensaje de bienvenida se
// alcance a leer antes de redirigir.
const WELCOME_PAUSE_MS = 1600;

type Status = 'loading' | 'ready' | 'invalid' | 'saving' | 'welcome';

/**
 * Pantalla única de "crea tu contraseña" para las 3 variantes del mismo token (ver
 * PasswordAuthService en el backend): invitado por un admin, auto-registro sin Google, y
 * "olvidé mi contraseña" — solo cambia el texto según tokenInfo().purpose, el formulario y el
 * flujo de activación son idénticos para las tres.
 */
@Component({
  selector: 'app-activate',
  imports: [IonHeader, IonToolbar, IonTitle, IonContent, IonSpinner, IonItem, IonInput, IonButton, IonIcon, RouterLink],
  templateUrl: './activate.html',
  styleUrl: './activate.scss',
})
export class Activate {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  protected readonly status = signal<Status>('loading');
  protected readonly purpose = signal<AuthTokenPurpose | null>(null);
  protected readonly displayName = signal('');
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly password = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly passwordVisible = signal(false);

  constructor() {
    if (!this.token) {
      this.status.set('invalid');
      return;
    }
    this.authService.getTokenInfo(this.token).subscribe({
      next: (info) => {
        this.purpose.set(info.purpose);
        this.displayName.set(info.displayName);
        this.status.set('ready');
      },
      error: () => this.status.set('invalid'),
    });
  }

  protected togglePasswordVisible(): void {
    this.passwordVisible.update((v) => !v);
  }

  protected get passwordsMismatch(): boolean {
    return this.confirmPassword().length > 0 && this.password() !== this.confirmPassword();
  }

  protected submit(): void {
    const password = this.password();
    if (password.length < 8 || this.passwordsMismatch || this.status() === 'saving') {
      return;
    }
    this.status.set('saving');
    this.errorMessage.set(null);
    this.authService.activateToken(this.token, password).subscribe({
      next: (response) => {
        this.status.set('welcome');
        setTimeout(() => this.router.navigateByUrl(homeRouteForRole(response.role)), WELCOME_PAUSE_MS);
      },
      error: (err: Error) => {
        this.status.set('ready');
        this.errorMessage.set(err.message || 'No pudimos activar tu cuenta. Intenta de nuevo.');
      },
    });
  }
}
