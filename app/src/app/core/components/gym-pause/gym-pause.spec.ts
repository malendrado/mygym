import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../services/auth.service';
import { GymPauseService, GymStatus } from '../../services/gym-pause.service';
import { GymPause } from './gym-pause';

const PAUSED: GymStatus = {
  paused: true,
  gymName: 'XaloGym',
  gymContact: { whatsappNumber: '+56 9 1111 2222', email: 'dueno@xalogym.cl', instagramUrl: 'https://instagram.com/xalogym' },
  mygymContact: { whatsappNumber: '56964641042', email: 'contacto@mygym.cl', instagramUrl: null },
};

function render(audience: 'member' | 'staff', status: GymStatus | null) {
  const state = signal<GymStatus | null>(status);
  const logout = vi.fn(async () => undefined);
  const navigate = vi.fn(async () => true);
  TestBed.configureTestingModule({
    providers: [
      {
        provide: GymPauseService,
        useValue: {
          status: state.asReadonly(),
          paused: () => state()?.paused === true,
          check: vi.fn(async () => undefined),
          watch: vi.fn(),
          unwatch: vi.fn(),
        },
      },
      { provide: AuthService, useValue: { logout } },
      { provide: Router, useValue: { navigate } },
    ],
  });
  const fixture = TestBed.createComponent(GymPause);
  fixture.componentInstance.audience = audience;
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, logout, navigate };
}

const text = (el: HTMLElement) => (el.textContent ?? '').replace(/\s+/g, ' ').trim();
const links = (el: HTMLElement) => Array.from(el.querySelectorAll('a.gym-pause__contact')).map((a) => a.getAttribute('href'));

describe('GymPause (gimnasio en pausa)', () => {
  it('shows nothing while the gym is active', () => {
    expect(render('member', null).el.querySelector('.gym-pause')).toBeNull();
  });

  it('a member is told, calmly, to contact HIS gym (WhatsApp, owner email, Instagram)', () => {
    const { el } = render('member', PAUSED);
    expect(text(el)).toContain('XaloGym está haciendo una pausa en mygym');
    expect(text(el)).toContain('Tus datos y tu membresía están resguardados');
    expect(text(el)).toContain('escríbele directamente a tu gimnasio');
    expect(links(el)).toEqual(['https://wa.me/56911112222', 'mailto:dueno@xalogym.cl', 'https://instagram.com/xalogym']);
    expect(text(el)).not.toMatch(/desactivad|bloquead/i); // el tono es de pausa, no de castigo
  });

  it('the gym team is told to contact mygym, with the mygym WhatsApp and email only', () => {
    const { el } = render('staff', PAUSED);
    expect(text(el)).toContain('La cuenta de XaloGym en mygym está en pausa');
    expect(text(el)).toContain('Tus datos y los de tus socios están resguardados');
    expect(links(el)).toEqual(['https://wa.me/56964641042', 'mailto:contacto@mygym.cl']);
  });

  it('only shows the contact channels the gym actually has', () => {
    const { el } = render('member', { ...PAUSED, gymContact: { whatsappNumber: null, email: 'dueno@xalogym.cl', instagramUrl: null } });
    expect(links(el)).toEqual(['mailto:dueno@xalogym.cl']);
  });

  it('without any contact data it still explains what to do', () => {
    const { el } = render('member', { ...PAUSED, gymContact: { whatsappNumber: null, email: null, instagramUrl: null } });
    expect(links(el)).toEqual([]);
    expect(text(el)).toContain('consúltalo directamente en tu gimnasio');
  });

  it('never links to a non-http Instagram value (no javascript: links)', () => {
    const { el } = render('member', { ...PAUSED, gymContact: { whatsappNumber: null, email: null, instagramUrl: 'javascript:alert(1)' } });
    expect(links(el)).toEqual([]);
  });

  it('Salir closes the session and goes to the login', async () => {
    const { el, logout, navigate } = render('member', PAUSED);
    (el.querySelector('.gym-pause__logout') as HTMLElement).click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/login']));
    expect(logout).toHaveBeenCalled();
  });
});

// ---- Pausa por plan mygym sin activar o impago (BILLING)

function renderBilling(role: 'GYM_ADMIN' | 'PROFESOR' | 'MEMBER', billingStatus: 'NOT_STARTED' | 'SUSPENDED') {
  const state = signal<GymStatus | null>({
    ...PAUSED,
    pauseReason: 'BILLING',
    billingStatus,
  });
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: GymPauseService,
        useValue: {
          status: state.asReadonly(),
          paused: () => true,
          check: vi.fn(async () => undefined),
          watch: vi.fn(),
          unwatch: vi.fn(),
        },
      },
      { provide: AuthService, useValue: { logout: vi.fn(), currentUser: () => ({ role }) } },
      { provide: Router, useValue: { navigate: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(GymPause);
  fixture.componentInstance.audience = role === 'MEMBER' ? 'member' : 'staff';
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('GymPause por falta de pago del plan mygym', () => {
  it('the administrator of a suspended gym sees the payment card inside the notice', () => {
    const el = renderBilling('GYM_ADMIN', 'SUSPENDED');
    expect(text(el)).toContain('El plan mygym de XaloGym está vencido');
    expect(text(el)).toContain('Paga con Flow y tu gimnasio se reactiva al instante; si transfieres, se reactiva cuando confirmemos el pago');
    expect(el.querySelector('app-gym-subscription')).not.toBeNull();
  });

  it('a teacher is told to ask the administrator and gets no payment card', () => {
    const el = renderBilling('PROFESOR', 'SUSPENDED');
    expect(text(el)).toContain('Pídele al administrador de tu gimnasio que renueve el plan');
    expect(el.querySelector('app-gym-subscription')).toBeNull();
  });

  it('a gym that was never activated says so, without asking anyone to pay inside the app', () => {
    const el = renderBilling('GYM_ADMIN', 'NOT_STARTED');
    expect(text(el)).toContain('todavía no está activada');
    expect(el.querySelector('app-gym-subscription')).toBeNull();
  });

  it('members keep the calm message of any pause: they never hear about the gym\'s billing', () => {
    const el = renderBilling('MEMBER', 'SUSPENDED');
    expect(text(el)).toContain('XaloGym está haciendo una pausa en mygym');
    expect(text(el)).not.toMatch(/plan mygym|pago|vencido/i);
  });
});
