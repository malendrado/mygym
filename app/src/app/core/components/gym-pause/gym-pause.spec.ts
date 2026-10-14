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
        useValue: { status: state.asReadonly(), paused: () => state()?.paused === true, check: vi.fn(async () => undefined) },
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
