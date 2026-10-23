import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../../environments/environment';
import { ContractView } from '../../models/subscription.model';
import { ContractSign } from './contract-sign';

const API = `${environment.apiUrl}/api/gym-admin/subscription/contract`;

const view: ContractView = {
  version: 'v1',
  signed: false,
  signedAt: null,
  signerName: null,
  signerCapacity: null,
  text: 'CONTRATO DE SERVICIOS MYGYM',
  blockedReason: null,
  maskedEmail: 'a***@gimnasio.cl',
  certificateNeeded: false,
};

type Form = {
  name: { set(v: string): void };
  rut: { set(v: string): void };
  adult: { set(v: boolean): void };
  powers: { set(v: boolean): void };
  code: { set(v: string): void; (): string };
  canSend(): boolean;
  canSign(): boolean;
  capacities(): { value: string }[];
  sendCode(): void;
  sign(): void;
  onCode(e: Event): void;
  capacity: { set(v: string): void; (): string };
};

function setup(kind: 'COMPANY' | 'PERSON' = 'COMPANY', loaded: ContractView = view) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ContractSign);
  fixture.componentInstance.kind = kind;
  fixture.detectChanges();
  http.expectOne(API).flush(loaded);
  fixture.detectChanges();
  const form = fixture.componentInstance as unknown as Form;
  const fill = () => {
    form.name.set('Ana Admin');
    form.rut.set('12.345.678-5');
    form.adult.set(true);
    form.powers.set(true);
    fixture.detectChanges();
  };
  return { fixture, http, form, fill, el: fixture.nativeElement as HTMLElement };
}

describe('ContractSign', () => {
  it('shows the contract text and offers only the capacities that fit the kind of invoice', () => {
    const company = setup('COMPANY');
    expect(company.el.textContent).toContain('CONTRATO DE SERVICIOS MYGYM');
    expect(company.form.capacity()).toBe('LEGAL_REP');
    expect(company.form.capacities().map((c) => c.value)).toEqual(['LEGAL_REP', 'ATTORNEY']);
    TestBed.resetTestingModule();
    const person = setup('PERSON');
    expect(person.form.capacity()).toBe('PERSON_OWNER');
    expect(person.form.capacities().map((c) => c.value)).toEqual(['PERSON_OWNER', 'ATTORNEY']);
  });

  it('says what is missing while the send button is off, and stops saying it once everything is filled', () => {
    const { form, fill, fixture, el } = setup();
    expect(el.querySelector('#cs-missing')?.textContent).toContain('Para continuar falta: tu nombre completo, tu RUT y aceptar el contrato.');
    form.name.set('Ana Admin');
    form.rut.set('1234');
    fixture.detectChanges();
    expect(el.querySelector('#cs-missing')?.textContent).toContain('falta: un RUT válido y aceptar el contrato.');
    fill();
    expect(el.querySelector('#cs-missing')).toBeNull();
  });

  it('the code can only be requested with name, a valid RUT and both declarations', () => {
    const { form, fill } = setup();
    expect(form.canSend()).toBe(false);
    fill();
    expect(form.canSend()).toBe(true);
    form.rut.set('12.345.678-9');
    expect(form.canSend()).toBe(false);
    form.rut.set('12.345.678-5');
    form.powers.set(false);
    expect(form.canSend()).toBe(false);
  });

  it('sends the declared data, then signs with the six digits and tells the parent', () => {
    const { fixture, form, fill, http } = setup();
    let signedEvents = 0;
    fixture.componentInstance.signed.subscribe(() => signedEvents++);
    fill();
    form.sendCode();
    const sent = http.expectOne(`${API}/code`);
    expect(sent.request.body).toEqual({ signerName: 'Ana Admin', signerRut: '12.345.678-5', capacity: 'LEGAL_REP', adult: true, powers: true });
    sent.flush({ maskedEmail: 'a***@gimnasio.cl' });

    // Solo dígitos: lo que no es número se descarta al escribir.
    form.onCode({ detail: { value: '12a3-456' } } as unknown as Event);
    expect(form.code()).toBe('123456');
    form.code.set('12345');
    expect(form.canSign()).toBe(false);
    form.code.set('123456');
    form.sign();
    const sign = http.expectOne(`${API}/sign`);
    expect(sign.request.body).toEqual({ code: '123456' });
    sign.flush({ required: true, signed: true, version: 'v1', signedAt: '2026-10-21T15:00:00Z', signerName: 'Ana Admin', signerCapacity: 'LEGAL_REP', certificateNeeded: false });
    expect(signedEvents).toBe(1);
  });

  it('when the server says it is not ready yet it shows the reason and no form', () => {
    const { el } = setup('COMPANY', {
      ...view,
      text: null,
      blockedReason: 'Completa primero los datos del contrato y la factura (RUT, razón social o nombre, giro, dirección y correo).',
    });
    expect(el.textContent).toContain('Completa primero los datos del contrato y la factura');
    expect(el.querySelector('ion-input')).toBeNull();
  });
  it('shows the signer data in the contract as it is typed, with brackets for what is still missing', () => {
    const { fixture, form, el } = setup('COMPANY', {
      ...view,
      text: 'representada por {{signerName}}, RUT {{signerRut}}, {{signerCapacity}} (el Cliente), correo a@gimnasio.cl. Administrador: {{signerName}}',
    });
    const text = () => el.querySelector('.cs__text')!.textContent;
    expect(text()).toContain('representada por [nombre de quien firma], RUT [RUT de quien firma], en calidad de representante legal');
    expect(text()).not.toContain('____');

    form.name.set('  Ana   Admin ');
    form.rut.set('123456785');
    fixture.detectChanges();
    expect(text()).toContain('representada por Ana Admin, RUT 12.345.678-5, en calidad de representante legal (el Cliente), correo a@gimnasio.cl. Administrador: Ana Admin');

    form.capacity.set('ATTORNEY');
    fixture.detectChanges();
    expect(text()).toContain('en calidad de apoderado');
  });

  it('a code requested before editing the signer data stops being usable: a new one has to be requested', () => {
    const { fixture, form, fill, http, el } = setup();
    fill();
    form.sendCode();
    http.expectOne(`${API}/code`).flush({ maskedEmail: 'a***@gimnasio.cl' });
    fixture.detectChanges();
    expect(el.querySelector('.cs__code')).not.toBeNull();

    form.name.set('Otra Persona');
    fixture.detectChanges();
    expect(el.querySelector('.cs__code')).toBeNull();
  });
});
