import { TestBed } from '@angular/core/testing';
import { GymLoader } from './gym-loader';

function render(inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(GymLoader);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('GymLoader', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('announces what is happening to screen readers and shows the mygym logo when the gym has none', () => {
    const { el } = render({ message: 'Buscando socios…' });
    const status = el.querySelector('[role="status"]')!;
    expect(status.getAttribute('aria-label')).toBe('Buscando socios…');
    expect(el.querySelector('.gym-loader__logo')!.getAttribute('src')).toBe('favicon.svg');
    expect(el.querySelector('.gym-loader__bell')).not.toBeNull();
  });

  it('shows the logo of the gym when there is one', () => {
    const { el } = render({ logo: 'data:image/svg+xml;utf8,%3Csvg%2F%3E' });
    expect(el.querySelector('.gym-loader__logo')!.getAttribute('src')).toContain('data:image/svg+xml');
  });

  it('the compact version has only ring and weight, without texts', () => {
    const { el } = render({ compact: true, message: 'Cargando…' });
    expect(el.querySelector('.gym-loader__message')).toBeNull();
    expect(el.querySelector('.gym-loader--compact')).not.toBeNull();
  });

  it('rotates its short phrases while the wait lasts and stops when it is destroyed', () => {
    const { fixture, el } = render({ tips: ['Calentando motores…', 'Cargando las pesas…'] });
    expect(el.querySelector('.gym-loader__tip')!.textContent).toContain('Calentando motores…');

    vi.advanceTimersByTime(2400);
    fixture.detectChanges();
    expect(el.querySelector('.gym-loader__tip')!.textContent).toContain('Cargando las pesas…');

    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});
