import { ApplicationConfig, inject, isDevMode, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideIonicAngular } from '@ionic/angular';
import { provideServiceWorker } from '@angular/service-worker';
import { webRoutes } from './web.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { provideGoogleAuth } from './core/providers/google-auth.providers';
import { PwaInstallService } from './core/services/pwa-install.service';

export const webConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(webRoutes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideIonicAngular({}),
    provideGoogleAuth(),
    provideAppInitializer(() => {
      inject(PwaInstallService);
    }),
    // Sin esto Android nunca instala un WebAPK "standalone" de verdad — Add-to-Home-Screen
    // sin service worker crea un simple acceso directo que abre en una pestaña normal de
    // Chrome. Eso hacía que matchMedia('(display-mode: standalone)') nunca diera true en
    // Android, y AuthService.sessionStore() guardaba siempre en sessionStorage (se borra al
    // cerrar) en vez de localStorage — bug real reportado: sesión de super-admin perdida cada
    // vez que se reabría la app instalada.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ]
};
