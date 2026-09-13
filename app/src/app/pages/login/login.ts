import { Component, DestroyRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonContent, IonHeader, IonSpinner, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { AuthService } from '../../core/services/auth.service';

// Si el callback de Google Identity Services nunca llega (bloqueado por un
// navegador embebido tipo WhatsApp/Instagram, o por restricciones de cookies
// de terceros en Safari), sin este timeout la pantalla queda "pegada" en
// silencio para siempre — sin error, sin señal de qué pasó.
const SIGN_IN_TIMEOUT_MS = 15000;

// Mismo criterio que join.ts: una pausa deliberada para que el mensaje de
// bienvenida se alcance a leer antes de redirigir.
const WELCOME_PAUSE_MS = 1600;

@Component({
  selector: 'app-login',
  imports: [IonHeader, IonToolbar, IonTitle, IonContent, IonText, IonSpinner, GoogleSigninButtonDirective],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errorMessage = signal<string | null>(null);
  protected readonly signingIn = signal(false);
  protected readonly showWelcome = signal(false);
  private loggedIn = false;
  private timeoutHandle: ReturnType<typeof setTimeout> | null = null;

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
            const destination =
              response.role === 'SUPER_ADMIN' ? '/admin/gyms' : response.role === 'GYM_ADMIN' ? '/gym-admin' : '/member';
            this.router.navigate([destination]);
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
      if (document.visibilityState === 'visible' && !this.loggedIn) {
        this.armTimeout();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisible);
      this.clearTimeout();
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
