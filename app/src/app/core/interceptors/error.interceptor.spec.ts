import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { SOCIAL_AUTH_CONFIG } from '@abacritt/angularx-social-login';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        // AuthService (que el interceptor inyecta para la rama del 410) inyecta SocialAuthService,
        // que a su vez requiere este token o falla con NG0201 — mismo workaround que
        // google-auth.providers.ts.
        { provide: SOCIAL_AUTH_CONFIG, useValue: { autoLogin: false, providers: [] } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  it('maps ProblemDetail.detail to the error message', () => {
    let captured: Error | undefined;
    http.get('/api/gyms/999').subscribe({ error: (err) => (captured = err) });

    httpMock.expectOne('/api/gyms/999').flush(
      { title: 'Gym not found', detail: 'Gym not found: id=999' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(captured?.message).toBe('Gym not found: id=999');
  });

  it('falls back to a generic message when the body has no detail or title', () => {
    let captured: Error | undefined;
    http.get('/api/gyms/999').subscribe({ error: (err) => (captured = err) });

    httpMock.expectOne('/api/gyms/999').flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(captured?.message).toBe('Ocurrió un error inesperado. Intenta nuevamente.');
  });

  it('preserves the original HTTP status on the thrown Error', () => {
    // Regresión real: sin esto, tv-screen.ts nunca detectaba una pantalla desvinculada (404),
    // gym-admin.ts nunca mostraba el mensaje de código vencido (404), y gym-form.ts nunca
    // distinguía 409/502 al desvincular un gimnasio — todos revisan err.status más abajo.
    let captured: (Error & { status?: number }) | undefined;
    http.get('/api/public/tv/screens/abc').subscribe({ error: (err) => (captured = err) });

    httpMock.expectOne('/api/public/tv/screens/abc').flush(null, { status: 404, statusText: 'Not Found' });

    expect(captured?.status).toBe(404);
  });

  it('sends a revoked session to /login without emitting an error', async () => {
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    let sawError = false;
    http.get('/api/me/reservations').subscribe({ error: () => (sawError = true) });

    httpMock.expectOne('/api/me/reservations').flush(
      { title: 'Sesión cerrada', detail: 'Tu sesión ya no es válida.', code: 'SESSION_REVOKED' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await new Promise((resolve) => setTimeout(resolve));

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect(sawError).toBe(false);
  });

  it('does not treat an ordinary 401 (for example a wrong password) as a revoked session', () => {
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    let captured: Error | undefined;
    http.post('/api/public/auth/login', {}).subscribe({ error: (err) => (captured = err) });

    httpMock.expectOne('/api/public/auth/login').flush(
      { title: 'Credenciales inválidas', detail: 'Correo o contraseña incorrectos.' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(captured?.message).toBe('Correo o contraseña incorrectos.');
  });

  it('redirects to the contact form on a 403 from the login endpoint, without emitting an error', () => {
    // Bug real encontrado en la práctica: antes caía en la regla genérica de 403+no-GET de abajo
    // y mostraba "esta es una demo de solo lectura" a alguien que nunca tuvo nada que ver con
    // mygym — acá se verifica que en vez de eso se redirige, sin dejar ningún error pendiente.
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    let completed = false;
    let sawError = false;
    http.post('/api/auth/google', { idToken: 'x' }).subscribe({
      error: () => (sawError = true),
      complete: () => (completed = true),
    });

    httpMock.expectOne('/api/auth/google').flush(
      { title: 'Cuenta no registrada', detail: 'No hay una cuenta registrada para este email de Google: email=x' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(navigateSpy).toHaveBeenCalledWith(['/'], { queryParams: { contacto: 'cuenta-no-registrada' } });
    expect(sawError).toBe(false);
    expect(completed).toBe(true);
  });
});
