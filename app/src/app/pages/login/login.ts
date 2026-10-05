import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IonButton, IonContent, IonHeader, IonIcon, IonInput, IonItem, IonSpinner, IonTitle, IonToolbar } from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { addIcons } from 'ionicons';
import { alertCircleOutline, eyeOffOutline, eyeOutline } from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { homeRouteForRole } from '../../core/models/auth.model';

addIcons({ 'eye-outline': eyeOutline, 'eye-off-outline': eyeOffOutline, 'alert-circle-outline': alertCircleOutline });

// Si el callback de Google Identity Services nunca llega (bloqueado por un
// navegador embebido tipo WhatsApp/Instagram, o por restricciones de cookies
// de terceros en Safari), sin este timeout la pantalla queda "pegada" en
// silencio para siempre — sin error, sin señal de qué pasó.
const SIGN_IN_TIMEOUT_MS = 15000;

// Mismo criterio que join.ts: una pausa deliberada para que el mensaje de
// bienvenida se alcance a leer antes de redirigir.
const WELCOME_PAUSE_MS = 1600;

type LoginMode = 'google' | 'password' | 'forgot';

@Component({
  selector: 'app-login',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonSpinner,
    IonItem,
    IonInput,
    IonButton,
    IonIcon,
    GoogleSigninButtonDirective,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errorMessage = signal<string | null>(null);
  protected readonly signingIn = signal(false);
  protected readonly showWelcome = signal(false);
  private loggedIn = false;
  private timeoutHandle: ReturnType<typeof setTimeout> | null = null;

  // Google arriba de siempre, con un toggle debajo para quien prefiera no usarlo — ver
  // diseño acordado con el usuario (brainstorming de auth 2026-10-02): misma pantalla, un
  // switch, no una ruta separada.
  protected readonly mode = signal<LoginMode>('google');
  protected readonly loginEmail = signal('');
  protected readonly loginPassword = signal('');
  protected readonly passwordVisible = signal(false);
  protected readonly passwordSubmitting = signal(false);

  protected readonly forgotEmail = signal('');
  protected readonly forgotSubmitting = signal(false);
  protected readonly forgotSent = signal(false);

  constructor() {
    this.socialAuthService.authState.subscribe((user) => {
      if (!user?.idToken) {
        return;
      }
      this.clearTimeout();
      this.signingIn.set(true);
      this.errorMessage.set(null);
      this.authService.loginWithGoogle(user.idToken).subscribe({
        next: (response) => {
          this.loggedIn = true;
          this.showWelcome.set(true);
          setTimeout(() => {
            // returnUrl solo la honra un MEMBER real (ver checkin.ts) — un admin que haya
            // llegado a /login con ese query param igual cae en su destino normal por rol.
            const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
            const destination =
              response.role === 'MEMBER' && returnUrl?.startsWith('/') && !returnUrl.startsWith('//')
                ? returnUrl
                : homeRouteForRole(response.role);
            this.router.navigateByUrl(destination);
          }, WELCOME_PAUSE_MS);
        },
        error: (err: Error) => {
          this.signingIn.set(false);
          this.errorMessage.set(err.message || 'No pudimos iniciar sesión.');
        },
      });
    });

    // El botón de Google es un iframe de origen cruzado — no podemos engancharle
    // un (click) propio para saber cuándo el usuario intentó entrar. En su lugar,
    // "el usuario volvió de Google" se detecta como la pestaña recuperando
    // visibilidad: si eso pasa y ni el login ni un error ya llegaron, algo se
    // quedó pegado (típico de navegadores embebidos de WhatsApp/Instagram, o
    // restricciones de cookies de terceros en Safari) — mostramos una salida.
    const onVisible = () => {
      if (document.visibilityState === 'visible' && !this.loggedIn && this.mode() === 'google') {
        this.armTimeout();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisible);
      this.clearTimeout();
    });
  }

  protected setMode(mode: LoginMode): void {
    this.mode.set(mode);
    this.errorMessage.set(null);
  }

  protected togglePasswordVisible(): void {
    this.passwordVisible.update((v) => !v);
  }

  protected submitPasswordLogin(): void {
    const email = this.loginEmail().trim();
    const password = this.loginPassword();
    if (!email || !password || this.passwordSubmitting()) {
      return;
    }
    this.passwordSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.loginWithPassword(email, password).subscribe({
      next: (response) => {
        this.loggedIn = true;
        this.passwordSubmitting.set(false);
        this.showWelcome.set(true);
        this.mode.set('google'); // reusa el mismo bloque de bienvenida de abajo
        setTimeout(() => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const destination =
            response.role === 'MEMBER' && returnUrl?.startsWith('/') && !returnUrl.startsWith('//')
              ? returnUrl
              : homeRouteForRole(response.role);
          this.router.navigateByUrl(destination);
        }, WELCOME_PAUSE_MS);
      },
      error: (err: Error) => {
        this.passwordSubmitting.set(false);
        this.errorMessage.set(err.message || 'Email o contraseña incorrectos.');
      },
    });
  }

  protected submitForgotPassword(): void {
    const email = this.forgotEmail().trim();
    if (!email || this.forgotSubmitting()) {
      return;
    }
    this.forgotSubmitting.set(true);
    this.authService.requestPasswordReset(email).subscribe({
      next: () => {
        this.forgotSubmitting.set(false);
        this.forgotSent.set(true);
      },
      error: () => {
        // Mismo mensaje genérico aunque algo falle de verdad — nunca distinguir motivos.
        this.forgotSubmitting.set(false);
        this.forgotSent.set(true);
      },
    });
  }

  private armTimeout(): void {
    this.clearTimeout();
    this.timeoutHandle = setTimeout(() => {
      if (this.loggedIn || this.signingIn()) {
        return;
      }
      this.errorMessage.set(
        'El ingreso con Google está demorando más de lo normal. Si usas Brave o un navegador con bloqueo de cookies/rastreadores activado (o si abriste este link desde WhatsApp/Instagram), desactívalo para este sitio o ábrelo en Chrome/Safari e intenta de nuevo.',
      );
    }, SIGN_IN_TIMEOUT_MS);
  }

  private clearTimeout(): void {
    if (this.timeoutHandle) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }
  }
}
