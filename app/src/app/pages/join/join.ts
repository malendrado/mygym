import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonButton,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  checkmarkCircle,
  eyeOffOutline,
  eyeOutline,
  informationCircleOutline,
  logoInstagram,
  logoWhatsapp,
  sparklesOutline,
} from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { LoginResponse } from '../../core/models/auth.model';
import { GymPhoto, MemberPlan, PublicGym } from '../../core/models/gym.model';
import { deriveSurfaceTint, ensureMinContrastColor, syncThemeOverrides } from '../../core/utils/gym-theme';
import { toLogoImgSrc } from '../../core/utils/logo-src';

addIcons({
  'logo-instagram': logoInstagram,
  'logo-whatsapp': logoWhatsapp,
  'information-circle-outline': informationCircleOutline,
  'sparkles-outline': sparklesOutline,
  'eye-outline': eyeOutline,
  'eye-off-outline': eyeOffOutline,
  'alert-circle-outline': alertCircleOutline,
  'checkmark-circle': checkmarkCircle,
});

type Status = 'loading' | 'ready' | 'not-found' | 'joining' | 'welcome';
type JoinMode = 'google' | 'password';

// Mismo gotcha que login.ts: si el callback de Google nunca llega (navegador
// embebido de WhatsApp/Instagram, restricciones de cookies de terceros en
// Safari), la página se queda en 'ready' para siempre sin ninguna señal.
const SIGN_IN_TIMEOUT_MS = 15000;

// Tras un alta/login exitoso, el usuario ve un mensaje de bienvenida
// personalizado (nuevo socio vs. reingreso) antes de redirigir — sin esta
// pausa deliberada, la redirección es tan rápida que el mensaje nunca
// alcanza a leerse.
const WELCOME_PAUSE_MS = 1600;

// Cada cuánto rota sola la foto principal, y cuánto se queda quieta tras un toque manual.
const HERO_ROTATE_MS = 5000;
const HERO_PAUSE_AFTER_TAP_MS = 12000;

// Plan que el visitante tocó acá: se recuerda hasta /member, que lo destaca al entrar.
export const PREFERRED_PLAN_KEY = 'mygym.preferredPlan';

@Component({
  selector: 'app-join',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonText,
    IonIcon,
    IonSpinner,
    IonItem,
    IonInput,
    IonButton,
    GoogleSigninButtonDirective,
  ],
  templateUrl: './join.html',
  styleUrl: './join.scss',
})
export class Join {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gymService = inject(GymService);
  private readonly authService = inject(AuthService);
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  private timeoutHandle: ReturnType<typeof setTimeout> | null = null;

  protected readonly status = signal<Status>('loading');
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly plans = signal<MemberPlan[]>([]);
  protected readonly plansLoaded = signal(false);
  protected readonly photos = signal<GymPhoto[]>([]);
  protected readonly isNewMember = signal<boolean | null>(null);

  // Foto principal del encabezado + plan que el visitante marcó como su interés.
  protected readonly activePhoto = signal(0);
  protected readonly selectedPlanId = signal<number | null>(null);
  protected readonly selectedPlanName = computed(
    () => this.plans().find((plan) => plan.id === this.selectedPlanId())?.name ?? null,
  );
  private rotationPausedUntil = 0;

  // Alternativa a Google sin costo (ver brainstorming de auth 2026-10-02) — por defecto arranca
  // en 'google' solo si el gym lo tiene habilitado; si no, el formulario de contraseña es la
  // ÚNICA opción (antes esto era un callejón sin salida, ver el @else de abajo en join.html).
  protected readonly joinMode = signal<JoinMode>('google');
  protected readonly registerName = signal('');
  protected readonly registerEmail = signal('');
  protected readonly registerSubmitting = signal(false);
  protected readonly registerSent = signal(false);
  protected readonly passwordVisible = signal(false);

  protected readonly instagramUrl = computed(() => this.gym()?.instagramUrl || null);
  protected readonly whatsappLink = computed(() => {
    const number = this.gym()?.whatsappNumber;
    return number ? `https://wa.me/${number.replace(/[^\d]/g, '')}` : null;
  });

  protected readonly themeSurface = computed(() =>
    deriveSurfaceTint(this.gym()?.themeColor ?? '#c6ff3d', this.gym()?.themeMode),
  );
  // Ver el mismo comentario en member.ts — el acento libre a veces no llega
  // a 4.5:1 usado como texto plano (ej. el precio de un plan).
  protected readonly accentTextSafe = computed(() =>
    ensureMinContrastColor(this.gym()?.themeColor ?? '#c6ff3d', this.themeSurface().card),
  );
  // Mismo criterio que member.ts (withHighlight): con 3+ planes, el de precio
  // intermedio se marca "Recomendado" — heurística visual pura, no una señal
  // del admin. Con 1-2 planes no hay "intermedio" real, así que no se destaca
  // ninguno (evita un badge arbitrario en un plan único o en el más caro).
  protected readonly highlightedPlanId = computed<number | null>(() => {
    const list = this.plans();
    if (list.length < 3) {
      return null;
    }
    const sorted = [...list].sort((a, b) => a.priceClp - b.priceClp);
    return sorted[Math.floor(sorted.length / 2)].id;
  });

  protected readonly logoSrc = computed(() => toLogoImgSrc(this.gym()?.logoSvg));

  constructor() {
    this.gymService.getPublicBySlug(this.slug).subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.status.set('ready');
        if (!gym.googleLoginEnabled) {
          this.joinMode.set('password');
        }
      },
      error: () => this.status.set('not-found'),
    });

    // Modo claro necesita más que --join-bg/--join-card (bindeadas inline abajo) — también
    // los tokens base globales (--brand-ink, --ion-color-danger, el step-ramp de Ionic) que
    // hoy asumen "siempre oscuro" en styles.scss. Ver gym-theme.ts:syncThemeOverrides.
    effect(() => {
      const currentGym = this.gym();
      syncThemeOverrides(currentGym?.themeMode, currentGym?.themeColor);
    });
    this.destroyRef.onDestroy(() => syncThemeOverrides('DARK', null));

    this.gymService.getPublicPlansBySlug(this.slug).subscribe({
      next: (plans) => {
        this.plans.set(plans);
        this.plansLoaded.set(true);
        if (!plans.some((plan) => plan.id === this.selectedPlanId())) {
          this.selectedPlanId.set(null);
        }
      },
      error: () => this.plansLoaded.set(true),
    });

    this.gymService.getPublicPhotosBySlug(this.slug).subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => {
        // Best-effort: sin fotos, la página sigue funcionando igual.
      },
    });

    this.gymService.recordVisit('JOIN', this.slug);

    this.restorePreferredPlan();
    this.startPhotoRotation();

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
          this.isNewMember.set(response.isNewMember);
          this.status.set('welcome');
          setTimeout(() => {
            const destination =
              response.role === 'SUPER_ADMIN'
                ? '/admin/gyms'
                : response.role === 'GYM_ADMIN' || response.role === 'DEMO_ADMIN'
                  ? '/gym-admin'
                  : '/member';
            this.router.navigate([destination]);
          }, WELCOME_PAUSE_MS);
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

  protected setJoinMode(mode: JoinMode): void {
    this.joinMode.set(mode);
    this.errorMessage.set(null);
  }

  protected togglePasswordVisible(): void {
    this.passwordVisible.update((v) => !v);
  }

  // Nunca inicia sesión de inmediato (a diferencia de Google) — la cuenta recién se crea al
  // confirmar el token de activación que llega por correo (ver AuthService.registerSelf).
  // Mensaje final siempre genérico, exista o no ya una cuenta con ese email.
  protected submitSelfRegister(): void {
    const name = this.registerName().trim();
    const email = this.registerEmail().trim();
    if (!name || !email || this.registerSubmitting()) {
      return;
    }
    this.registerSubmitting.set(true);
    this.errorMessage.set(null);
    this.authService.registerSelf(name, email, this.slug).subscribe({
      next: () => {
        this.registerSubmitting.set(false);
        this.registerSent.set(true);
      },
      error: () => {
        this.registerSubmitting.set(false);
        this.registerSent.set(true);
      },
    });
  }

  // La foto principal rota sola mientras la pestaña esté visible; un toque en una miniatura la
  // pausa unos segundos. Con "reducir movimiento" activado en el sistema no rota nunca.
  private startPhotoRotation(): void {
    if (typeof window === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    const timer = setInterval(() => {
      const total = this.photos().length;
      if (total < 2 || document.visibilityState !== 'visible' || Date.now() < this.rotationPausedUntil) {
        return;
      }
      this.activePhoto.update((index) => (index + 1) % total);
    }, HERO_ROTATE_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  protected showPhoto(index: number): void {
    this.activePhoto.set(index);
    this.rotationPausedUntil = Date.now() + HERO_PAUSE_AFTER_TAP_MS;
  }

  protected selectPlan(planId: number): void {
    const next = this.selectedPlanId() === planId ? null : planId;
    this.selectedPlanId.set(next);
    try {
      if (next === null) {
        localStorage.removeItem(PREFERRED_PLAN_KEY);
      } else {
        localStorage.setItem(PREFERRED_PLAN_KEY, JSON.stringify({ slug: this.slug, planId: next }));
      }
    } catch {
      // Sin almacenamiento (modo privado): la selección sigue sirviendo en esta pantalla.
    }
  }

  private restorePreferredPlan(): void {
    try {
      const raw = localStorage.getItem(PREFERRED_PLAN_KEY);
      const saved = raw ? (JSON.parse(raw) as { slug?: string; planId?: number }) : null;
      if (saved?.slug === this.slug && typeof saved.planId === 'number') {
        this.selectedPlanId.set(saved.planId);
      }
    } catch {
      // Un valor corrupto simplemente no preselecciona nada.
    }
  }

  protected formatClp(value: number): string {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(value);
  }

  protected quotaLabel(plan: MemberPlan): string {
    return plan.monthlyClasses === null ? 'Clases ilimitadas' : `${plan.monthlyClasses} clases al mes`;
  }
}
