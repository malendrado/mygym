import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MygymSettings } from '../../../../core/models/mygym-settings.model';
import { MygymContactService } from '../../../../core/services/mygym-contact.service';
import { MygymSettingsPage } from './mygym-settings';

const current: MygymSettings = {
  whatsapp: '56964641042',
  contactEmail: 'contacto@mygym.cl',
  companyName: 'INFORMÁTICA LUIS CORTÉS ÁLVAREZ E.I.R.L.',
  companyRut: '76.831.567-1',
  legalRepName: 'Luis René Cortés Álvarez',
  legalRepRut: '16.302.718-6',
  companyAddress: 'La Unión 102, comuna de Puchuncaví',
  bankHolder: 'Titular',
  bankRut: '',
  bankName: 'Banco Estado',
  bankAccountType: 'Cuenta Corriente',
  bankAccountNumber: '123',
  bankEmail: 'contacto@mygym.cl',
  updatedAt: null,
};

type Form = { whatsapp: { set(v: string): void; (): string }; companyRut: { set(v: string): void }; save(): void; hasErrors(): boolean };

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(MygymSettingsPage);
  fixture.detectChanges();
  http.match((r) => r.url.endsWith('/api/public/mygym-contact')).forEach((r) => r.flush({ whatsapp: '56964641042', email: 'contacto@mygym.cl' }));
  http.expectOne((r) => r.url.endsWith('/api/mygym-settings') && r.method === 'GET').flush(current);
  fixture.detectChanges();
  return { http, fixture, form: fixture.componentInstance as unknown as Form, el: fixture.nativeElement as HTMLElement };
}

describe('MygymSettingsPage', () => {
  it('starts with what is in force today, so the form shows what the app already uses', () => {
    const { form, el } = setup();
    expect(form.whatsapp()).toBe('56964641042');
    expect(el.textContent).toContain('Empresa que contrata');
    expect(el.textContent).toContain('Cuenta para transferencias');
  });

  it('does not save while a RUT or the WhatsApp is invalid', () => {
    const { http, form, fixture, el } = setup();
    form.companyRut.set('76.831.567-9');
    form.whatsapp.set('+56 9 1234');
    fixture.detectChanges();

    form.save();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'PUT');
    expect(form.hasErrors()).toBe(true);
    expect(el.textContent).toContain('RUT no válido');
    expect(el.textContent).toContain('Solo dígitos con código de país');
  });

  it('saves the data and the rest of the app sees the new contact right away', () => {
    const { http, form, fixture } = setup();
    const contact = TestBed.inject(MygymContactService);
    form.whatsapp.set('56912345678');

    form.save();

    const put = http.expectOne((r) => r.url.endsWith('/api/mygym-settings') && r.method === 'PUT');
    expect((put.request.body as { whatsapp: string }).whatsapp).toBe('56912345678');
    put.flush({ ...current, whatsapp: '56912345678', contactEmail: 'nuevo@mygym.cl', updatedAt: '2026-10-23T12:00:00Z' });
    fixture.detectChanges();

    expect(contact.whatsapp()).toBe('56912345678');
    expect(contact.email()).toBe('nuevo@mygym.cl');
  });
});
