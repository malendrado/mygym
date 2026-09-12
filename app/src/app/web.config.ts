import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideIonicAngular } from '@ionic/angular';
import { webRoutes } from './web.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { provideGoogleAuth } from './core/providers/google-auth.providers';

export const webConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(webRoutes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideIonicAngular({}),
    provideGoogleAuth(),
  ]
};
