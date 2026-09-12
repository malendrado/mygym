import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { IonContent, IonHeader, IonSpinner, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { PublicGym } from '../../core/models/gym.model';
import { deriveSurfaceTint } from '../../core/utils/gym-theme';

type Status = 'loading' | 'ready' | 'not-found' | 'joining';

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

  private readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';

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
      this.status.set('joining');
      this.errorMessage.set(null);
      this.authService.joinGym(user.idToken, this.slug).subscribe({
        next: () => this.router.navigate(['/member']),
        error: (err: Error) => {
          this.status.set('ready');
          this.errorMessage.set(err.message || 'No pudimos completar tu inscripción. Intenta de nuevo.');
        },
      });
    });
  }
}
