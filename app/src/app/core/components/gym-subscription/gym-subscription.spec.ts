import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { environment } from '../../../../environments/environment';
import { GymSubscription } from '../../models/subscription.model';
import { GymPauseService } from '../../services/gym-pause.service';
import { formatClp, formatIsoDate, GymSubscriptionCard } from './gym-subscription';

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
    anchorDate: '2026-10-15',
    dueDate: '2026-11-15',
    suspensionDate: '2026-11-18',
    daysToDue: 25,
    canRenew: false,
    transferPending: false,
    pendingPlanId: null,
    pendingPlanName: null,
    pendingPlanFrom: null,
    billing: { rut: '76.123.456-7', name: 'Gimnasio SpA', giro: 'Servicios deportivos', address: 'Av. Siempre Viva 123', email: 'facturas@gimnasio.cl' },
    bank: { holder: 'Cortes Dev SpA', rut: '77.123.456-7', bank: 'Banco de Chile', accountType: 'Cuenta corriente', accountNumber: '123', email: 'contacto@mygym.cl' },
    payments: [],
    ...overrides,
  };
}

function render(mode: 'admin' | 'super', sub: GymSubscription) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: GymPauseService, useValue: { check: vi.fn(async () => undefined) } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(GymSubscriptionCard);
  fixture.componentInstance.mode = mode;
  if (mode === 'super') {
    fixture.componentInstance.gymId = 5;
  }
  fixture.detectChanges();
  if (mode === 'super') {
    http.expectOne(`${API}/api/mygym-plans`).flush([
      { id: 1, code: 'EMPIEZA', name: 'Empieza', clientsLabel: 'Hasta 100 socios', priceClp: 29990, maxUsers: 100, maxProfesores: 10, maxAdmins: 5, features: [], highlighted: false, sortOrder: 1, active: true },
    ]);
    http.expectOne(`${API}/api/gyms/5/subscription`).flush(sub);
  } else {
    http.expectOne(`${API}/api/gym-admin/subscription`).flush(sub);
  }
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, http, fixture };
}

const isDisabled = (b: Element | undefined) => !!b && (b.hasAttribute('disabled') || (b as unknown as { disabled?: boolean }).disabled === true);
const text = (el: HTMLElement) => (el.textContent ?? '').replace(/\s+/g, ' ').trim();

describe('formatos', () => {
  it('writes dates and amounts the Chilean way, without shifting the day', () => {
    expect(formatIsoDate('2026-11-15')).toBe('15 de noviembre de 2026');
    expect(formatIsoDate(null)).toBe('—');
    expect(formatClp(35688)).toBe('$35.688');
  });
});

describe('GymSubscriptionCard (administrador del gimnasio)', () => {
  it('shows an up-to-date plan without a renew button, only a hint about the last 3 days', () => {
    const { el } = render('admin', subscription());
    expect(text(el)).toContain('Al día');
    expect(text(el)).toContain('Próximo pago: 15 de noviembre de 2026');
    expect(text(el)).toContain('Total al mes$35.688');
    expect(text(el)).toContain('Podrás renovar desde 3 días antes del vencimiento');
    expect(text(el)).not.toContain('Pagar con Flow');
  });

  it('in the last 3 days it says so in words and offers Flow (with its fee) and transfer', () => {
    const { el } = render('admin', subscription({ daysToDue: 2, dueDate: '2026-10-21', canRenew: true }));
    expect(text(el)).toContain('Vence en 2 días');
    expect(text(el)).toContain('Pagar con Flow · $36.915');
    expect(text(el)).toContain('Pagar por transferencia');
    expect(text(el)).toContain('Si pagas con Flow se suma su comisión');
  });

  it('without billing data the form is required first and both payment ways stay locked', () => {
    const empty = { rut: null, name: null, giro: null, address: null, email: null };
    const { el } = render('admin', subscription({ daysToDue: 2, canRenew: true, billing: empty }));
    expect(text(el)).toContain('Datos para la factura (obligatorios)');
    expect(text(el)).toContain('Para pagar primero completa los datos de la factura');
    const buttons = Array.from(el.querySelectorAll('ion-button'));
    const flow = buttons.find((b) => (b.textContent ?? '').includes('Pagar con Flow'));
    const transfer = buttons.find((b) => (b.textContent ?? '').includes('Pagar por transferencia'));
    expect(isDisabled(flow)).toBe(true);
    expect(isDisabled(transfer)).toBe(true);
  });

  it('with billing data complete the form is collapsed and payment is open', () => {
    const { el } = render('admin', subscription({ daysToDue: 2, canRenew: true }));
    expect(text(el)).not.toContain('(obligatorios)');
    const flow = Array.from(el.querySelectorAll('ion-button')).find((b) => (b.textContent ?? '').includes('Pagar con Flow'));
    expect(isDisabled(flow)).toBe(false);
  });

  it('"Copiar todos los datos" puts the whole account on the clipboard, one labelled line per field', async () => {
    const writeText = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    const { el, fixture } = render('admin', subscription({ daysToDue: 2, canRenew: true }));
    (fixture.componentInstance as unknown as { toastController: unknown }).toastController = {
      create: async () => ({ present: async () => undefined }),
    };
    const transfer = Array.from(el.querySelectorAll('ion-button')).find((b) => (b.textContent ?? '').includes('Pagar por transferencia'));
    (transfer as HTMLElement).click();
    fixture.detectChanges();
    const copyAll = Array.from(el.querySelectorAll('ion-button')).find((b) => (b.textContent ?? '').includes('Copiar todos los datos'));
    (copyAll as HTMLElement).click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenCalledWith(
      'Nombre: Cortes Dev SpA\nRUT: 77.123.456-7\nBanco: Banco de Chile\nTipo de cuenta: Cuenta corriente\nNúmero de cuenta: 123\nCorreo: contacto@mygym.cl',
    );
    vi.unstubAllGlobals();
  });

  it('when the plan changes at renewal it says what the plan is today and shows the new plan with its own amounts', () => {
    const { el } = render('admin', subscription({
      daysToDue: 1, dueDate: '2026-10-20', canRenew: true,
      pendingPlanId: 2, pendingPlanName: 'Crece', pendingPlanFrom: '2026-10-20',
      chargePlanName: 'Crece', chargeClientsLabel: 'Hasta 200 socios', currentNetClp: 29990,
      netClp: 59990, ivaClp: 11398, totalClp: 71388, flowFeeClp: 2455, flowTotalClp: 73843,
    }));
    expect(text(el)).toContain('Cambio de plan el 20 de octubre: Empieza $29.990 pasa a→Crece $59.990 + IVA');
    expect(text(el)).toContain('CreceHasta 200 socios');
    expect(text(el)).toContain('Renovar con el plan Crece');
    expect(text(el)).toContain('Pagar con Flow · $73.843');
    expect(text(el)).not.toContain('pasa al plan');
  });

  it('a change scheduled for later keeps the current plan and amounts and only announces the date', () => {
    const { el } = render('admin', subscription({ pendingPlanId: 2, pendingPlanName: 'Crece', pendingPlanFrom: '2026-12-15' }));
    expect(text(el)).not.toContain('Cambio de plan el');
    expect(text(el)).toContain('Cambio programado: desde el 15 de diciembre de 2026 pasas al plan Crece');
  });

  it('a suspended gym is told it is paused for non-payment and can pay to reactivate', () => {
    const { el } = render('admin', subscription({ status: 'SUSPENDED', daysToDue: -5, canRenew: true }));
    expect(text(el)).toContain('En pausa por falta de pago');
    expect(text(el)).toContain('Pagar y reactivar');
  });

  it('while a transfer is under review it shows no payment buttons, so nobody pays twice', () => {
    const { el } = render('admin', subscription({ canRenew: true, daysToDue: 1, transferPending: true }));
    expect(text(el)).toContain('Estamos revisando tu transferencia');
    expect(text(el)).not.toContain('Pagar con Flow');
  });

  it('an unstarted gym has no payment actions, just an honest message', () => {
    const { el } = render('admin', subscription({ status: 'NOT_STARTED', planId: null, planName: null, anchorDate: null, dueDate: null, daysToDue: null, netClp: null, canRenew: false }));
    expect(text(el)).toContain('Sin activar');
    expect(text(el)).toContain('Se activa cuando mygym recibe tu primer pago');
    expect(text(el)).not.toContain('Pagar con Flow');
  });

  it('an exempt gym says it has no charge', () => {
    const { el } = render('admin', subscription({ status: 'EXEMPT', exempt: true }));
    expect(text(el)).toContain('Sin cobro');
  });

  it('a custom plan with no price yet asks to write to mygym instead of showing amounts', () => {
    const { el } = render('admin', subscription({ netClp: null, ivaClp: null, totalClp: null, flowFeeClp: null, flowTotalClp: null, canRenew: true, daysToDue: 1 }));
    expect(text(el)).toContain('Plan a medida');
    expect(text(el)).toContain('Tu plan es a medida: escríbenos a contacto@mygym.cl');
  });
});

describe('GymSubscriptionCard (super-admin)', () => {
  it('an unstarted gym shows the activation form', () => {
    const { el } = render('super', subscription({ status: 'NOT_STARTED', planId: null, planName: null, anchorDate: null, dueDate: null, daysToDue: null, netClp: null }));
    expect(text(el)).toContain('Activar gimnasio');
    expect(text(el)).toContain('Ese primer mes queda registrado como pagado');
  });

  it('a transfer the gym reported gets confirm and reject actions', () => {
    const { el } = render('super', subscription({
      status: 'SUSPENDED',
      canRenew: true,
      transferPending: true,
      payments: [{ id: 12, method: 'TRANSFER', status: 'REPORTED', amountClp: 35688, netClp: 29990, ivaClp: 5698, flowFeeClp: 0, periodStart: null, periodEnd: null, createdAt: '2026-11-20T12:00:00Z', confirmedAt: null, transferDate: '2026-11-19', transferBank: 'Banco Estado', transferOperation: '99887', note: null }],
    }));
    expect(text(el)).toContain('Avisó una transferencia');
    expect(text(el)).toContain('Banco Estado');
    expect(text(el)).toContain('Confirmar y reactivar');
    expect(text(el)).toContain('Rechazar');
  });

  it('only the last paid payment can be voided', () => {
    const paid = (id: number) => ({ id, method: 'MANUAL' as const, status: 'PAID' as const, amountClp: 1, netClp: 1, ivaClp: 0, flowFeeClp: 0, periodStart: '2026-10-15', periodEnd: '2026-11-15', createdAt: '2026-10-15T12:00:00Z', confirmedAt: null, transferDate: null, transferBank: null, transferOperation: null, note: null });
    const { el } = render('super', subscription({ payments: [paid(20), paid(10)] }));
    expect(Array.from(el.querySelectorAll('ion-button')).filter((b) => (b.textContent ?? '').includes('Anular'))).toHaveLength(1);
  });
});
