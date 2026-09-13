import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { IonContent, IonHeader, IonSpinner, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { LoginResponse } from '../../core/models/auth.model';
import { PublicGym } from '../../core/models/gym.model';
import { deriveSurfaceTint } from '../../core/utils/gym-theme';

type Status = 'loading' | 'ready' | 'not-found' | 'joining';

// Mismo gotcha que login.ts: si el callback de Google nunca llega (navegador
// embebido de WhatsApp/Instagram, restricciones de cookies de terceros en
// Safari), la página se queda en 'ready' para siempre sin ninguna señal.
const SIGN_IN_TIMEOUT_MS = 15000;

@Component({
  selector: 'app-join',
  imports: [IonHeader, IonToolbar, IonTitle, IonContent, IonText, IonSpinner, GoogleSigninButtonDirective],
  templateUrl: './join.html',
  styleUrl: './join.scss',
})
export class Join {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gymService = inject(GymService);
  private readonly authService = inject(AuthService);
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  private readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  private timeoutHandle: ReturnType<typeof setTimeout> | null = null;

  protected readonly status = signal<Status>('loading');
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly themeSurface = computed(() => deriveSurfaceTint(this.gym()?.themeColor ?? '#c6ff3d'));
  protected readonly isRasterLogo = computed(() => (this.gym()?.logoSvg ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.gym()?.logoSvg;
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });

  constructor() {
    this.gymService.getPublicBySlug(this.slug).subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.status.set('ready');
      },
      error: () => this.status.set('not-found'),
    });

    this.socialAuthService.authState.subscribe((user) => {
      if (!user?.idToken || this.status() !== 'ready') {
        return;
      }
      this.clearTimeout();
      this.status.set('joining');
      this.errorMessage.set(null);
      this.authService.joinGym(user.idToken, this.slug).subscribe({
        // Only a brand-new email actually becomes a MEMBER of this gym; an email that already
        // existed (any role) just logs in as-is (see AuthService.joinGymWithGoogle), so send
        // them to their real home instead of forcing them into /member.
        next: (response: LoginResponse) => {
          const destination =
            response.role === 'SUPER_ADMIN' ? '/admin/gyms' : response.role === 'GYM_ADMIN' ? '/gym-admin' : '/member';
          this.router.navigate([destination]);
        },
        error: (err: Error) => {
          this.status.set('ready');
          this.errorMessage.set(err.message || 'No pudimos completar tu inscripción. Intenta de nuevo.');
        },
      });
    });

    // El botón de Google es un iframe de origen cruzado, así que no hay un
    // (click) propio que detectar — "volvió de Google" se infiere de que la
    // pestaña recupera visibilidad (se cerró el popup/otra pestaña). Si eso
    // pasa y seguimos en 'ready' sin avanzar, algo se quedó pegado.
    const onVisible = () => {
      if (document.visibilityState === 'visible' && this.status() === 'ready') {
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
      if (this.status() === 'ready') {
        this.errorMessage.set(
          'El ingreso con Google está demorando más de lo normal. Si usas Brave o un navegador con bloqueo de cookies/rastreadores activado (o si abriste este link desde WhatsApp/Instagram), desactívalo para este sitio o ábrelo en Chrome/Safari e intenta de nuevo.',
        );
      }
    }, SIGN_IN_TIMEOUT_MS);
  }

  private clearTimeout(): void {
    if (this.timeoutHandle) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }
  }
}
