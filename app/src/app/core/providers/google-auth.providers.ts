import { EnvironmentProviders, importProvidersFrom, makeEnvironmentProviders } from '@angular/core';
import { GoogleLoginProvider, SOCIAL_AUTH_CONFIG, SocialAuthServiceConfig, SocialLoginModule } from '@abacritt/angularx-social-login';
import { environment } from '../../../environments/environment';

const googleAuthConfig: SocialAuthServiceConfig = {
  autoLogin: false,
  providers: [
    {
      id: GoogleLoginProvider.PROVIDER_ID,
      provider: new GoogleLoginProvider(environment.googleClientId),
    },
  ],
};

// @abacritt/angularx-social-login@2.6.0 registers this config under the string token
// 'SocialAuthServiceConfig' instead of the actual SOCIAL_AUTH_CONFIG InjectionToken that
// SocialAuthService injects (NG0201 otherwise) — provide it directly to work around that bug.
export function provideGoogleAuth(): EnvironmentProviders {
  return makeEnvironmentProviders([
    importProvidersFrom(SocialLoginModule.initialize(googleAuthConfig)),
    { provide: SOCIAL_AUTH_CONFIG, useValue: googleAuthConfig },
  ]);
}
