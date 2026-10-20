import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../../environments/environment';
import { GymSubscription } from '../../models/subscription.model';
import { BillingBanner } from './billing-banner';

const API = environment.apiUrl;

function subscription(overrides: Partial<GymSubscription> = {}): GymSubscription {
  return {
    gymId: 5,
    status: 'ACTIVE',
    exempt: false,
    planId: 1,
    planName: 'Empieza',
    clientsLabel: 'Hasta 100 socios',
    netClp: 29990,
    ivaClp: 5698,
    totalClp: 35688,
    flowFeeClp: 1227,
    flowTotalClp: 36915,
    maxUsers: 100,
    priceOverrideClp: null,
    maxUsersOverride: null,
    anchorDate: '2026-09-20',
    dueDate: '2026-10-20',
    suspensionDate: '2026-10-23',
    daysToDue: 1,
    canRenew: true,
    transferPending: false,
    pendingPlanId: null,
    pendingPlanName: null,
    pendingPlanFrom: null,
    billing: { kind: 'COMPANY', rut: null, name: null, giro: null, address: null, email: null },
    bank: { holder: '', rut: '', bank: '', accountType: '', accountNumber: '', email: 'contacto@mygym.cl' },
    payments: [],
    ...overrides,
  };
}

function render(sub: GymSubscription) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(BillingBanner);
  fixture.detectChanges();
  http.expectOne(`${API}/api/gym-admin/subscription`).flush(sub);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const text = (el: HTMLElement) => (el.textContent ?? '').replace(/\s+/g, ' ').trim();

describe('BillingBanner (aviso de vencimiento)', () => {
  it('in the last days it warns in yellow, in words, with the date the gym would be paused', () => {
    const el = render(subscription());
    expect(text(el)).toContain('Tu plan mygym vence en 1 día');
    expect(text(el)).toContain('se pausa el 23 de octubre de 2026');
    expect(el.querySelector('.bb--warn')).not.toBeNull();
    expect(text(el)).toContain('Renovar ahora');
  });

  it('on the due date it says today', () => {
    expect(text(render(subscription({ daysToDue: 0 })))).toContain('vence hoy');
  });

  it('during the grace days it turns red and urgent', () => {
    const el = render(subscription({ status: 'GRACE', daysToDue: -1 }));
    expect(text(el)).toContain('Tu plan mygym está vencido');
    expect(el.querySelector('.bb--danger')).not.toBeNull();
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('is not shown when the plan is up to date, exempt, or a transfer is under review', () => {
    expect(render(subscription({ canRenew: false, daysToDue: 20 })).querySelector('.bb')).toBeNull();
  });

  it('a transfer under review hides it, so nobody is asked to pay twice', () => {
    TestBed.resetTestingModule();
    expect(render(subscription({ transferPending: true })).querySelector('.bb')).toBeNull();
  });

  it('an exempt gym never sees it', () => {
    TestBed.resetTestingModule();
    expect(render(subscription({ status: 'EXEMPT', exempt: true, canRenew: false })).querySelector('.bb')).toBeNull();
  });
});
