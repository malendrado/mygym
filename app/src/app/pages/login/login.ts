import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonContent, IonHeader, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { GoogleSigninButtonDirective, SocialAuthService } from '@abacritt/angularx-social-login';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [IonHeader, IonToolbar, IonTitle, IonContent, IonText, GoogleSigninButtonDirective],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly socialAuthService = inject(SocialAuthService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.socialAuthService.authState.subscribe((user) => {
      if (!user?.idToken) {
        return;
      }
      this.authService.loginWithGoogle(user.idToken).subscribe({
        next: (response) => {
          const destination =
            response.role === 'SUPER_ADMIN' ? '/admin/gyms' : response.role === 'GYM_ADMIN' ? '/gym-admin' : '/member';
          this.router.navigate([destination]);
        },
        error: (err: Error) => this.errorMessage.set(err.message || 'No pudimos iniciar sesión.'),
      });
    });
  }
}
