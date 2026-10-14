import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { GymLimits } from '../../models/gym.model';
import { GymLimitsPanel } from './gym-limits';

function render(limits: GymLimits | null, audience: 'admin' | 'super' = 'admin', compact = false): HTMLElement {
  const fixture = TestBed.createComponent(GymLimitsPanel);
  fixture.componentInstance.limits = limits;
  fixture.componentInstance.audience = audience;
  fixture.componentInstance.compact = compact;
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const usage = (used: number, max: number) => ({ used, max, free: Math.max(0, max - used) });
const squash = (text: string | null | undefined) => (text ?? '').replace(/\s+/g, ' ').trim();

describe('GymLimitsPanel', () => {
  it('renders nothing until the limits arrive', () => {
    expect(render(null).querySelector('.gl')).toBeNull();
  });

  it('shows how many are used and how many are left for each role', () => {
    const el = render({ members: usage(18, 50), profesores: usage(3, 10), admins: usage(2, 5) });
    // Cada fila tiene nombre, conteo y frase por separado (en el DOM no hay espacios entre bloques).
    const rows = Array.from(el.querySelectorAll('.gl__row')).map((r) =>
      ['.gl__name', '.gl__count', '.gl__hint'].map((sel) => squash(r.querySelector(sel)?.textContent)).join(' | '),
    );
    expect(rows[0]).toBe('Socios | 18 de 50 | Te quedan 32 cupos');
    expect(rows[1]).toBe('Profesores | 3 de 10 | Te quedan 7 cupos');
    expect(rows[2]).toBe('Administradores | 2 de 5 | Te quedan 3 cupos');
  });

  it('warns in text (not only color) when it is almost full, and says 1 cupo in singular', () => {
    const el = render({ members: usage(49, 50), profesores: usage(0, 10), admins: usage(0, 5) });
    const first = el.querySelector('.gl__row') as HTMLElement;
    expect(first.classList.contains('gl__row--near')).toBe(true);
    expect(first.textContent).toContain('Te queda 1 cupo');
  });

  it('says it is full, and tells the gym admin to write to mygym while the super-admin can change it himself', () => {
    const full: GymLimits = { members: usage(50, 50), profesores: usage(10, 10), admins: usage(5, 5) };
    const admin = render(full, 'admin');
    expect(admin.querySelector('.gl__row--full')?.textContent).toContain('contacto@mygym.cl');
    const superAdmin = render(full, 'super');
    expect(superAdmin.querySelector('.gl__row--full')?.textContent).toContain('puedes ampliarlo en Configuración');
    expect(superAdmin.textContent).toContain('Llegaste al máximo de profesores');
    expect(superAdmin.textContent).toContain('Llegaste al máximo de administradores');
  });

  it('a cap lowered below the usage reads as full, never negative', () => {
    const el = render({ members: usage(12, 10), profesores: usage(0, 10), admins: usage(0, 5) });
    expect((el.querySelector('.gl__row') as HTMLElement).textContent).toContain('12 de 10');
    expect(el.querySelector('.gl__row--full')).not.toBeNull();
  });

  it('the compact variant is a single line about members', () => {
    const el = render({ members: usage(47, 50), profesores: usage(0, 10), admins: usage(0, 5) }, 'admin', true);
    expect(el.querySelector('.gl')).toBeNull();
    expect(squash(el.querySelector('.gl-compact')?.textContent)).toContain('47 de 50 socios');
    expect(squash(el.querySelector('.gl-compact')?.textContent)).toContain('Te quedan 3 cupos');
  });
});
