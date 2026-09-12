import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
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
});
