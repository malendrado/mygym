import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MygymContactService } from './mygym-contact.service';

const URL = 'http://localhost:8080/api/public/mygym-contact';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const service = TestBed.inject(MygymContactService);
  const request = http.match((r) => r.url.endsWith('/api/public/mygym-contact'))[0];
  return { http, service, request };
}

describe('MygymContactService', () => {
  it('shows the usual contact until the server answers, then the configured one', () => {
    const { service, request } = setup();
    expect(request).toBeDefined();
    expect(service.email()).toBe('contacto@mygym.cl');
    expect(service.whatsapp()).toBe('56964641042');

    request.flush({ whatsapp: '56912345678', email: 'hola@nuevo.cl' });

    expect(service.email()).toBe('hola@nuevo.cl');
    expect(service.whatsappUrl('Hola mygym')).toBe('https://wa.me/56912345678?text=Hola%20mygym');
  });

  it('keeps the usual contact when the request fails or comes back empty', () => {
    const { service, request } = setup();
    request.flush({ whatsapp: '', email: ' ' });
    expect(service.email()).toBe('contacto@mygym.cl');
    expect(service.whatsapp()).toBe('56964641042');
    expect(URL).toContain('/api/public/mygym-contact');
  });
});
