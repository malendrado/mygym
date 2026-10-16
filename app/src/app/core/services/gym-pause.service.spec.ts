import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { GymPauseService, GymStatus } from './gym-pause.service';

const URL = `${environment.apiUrl}/api/gym-status`;

const PAUSED: GymStatus = {
  paused: true,
  gymName: 'XaloGym',
  gymContact: { whatsappNumber: '56911112222', email: 'dueno@xalogym.cl', instagramUrl: null },
  mygymContact: { whatsappNumber: '56964641042', email: 'contacto@mygym.cl', instagramUrl: null },
};

describe('GymPauseService', () => {
  let http: HttpTestingController;
  let service: GymPauseService;
  let reload: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(GymPauseService);
    reload = vi.fn<() => void>();
    (service as unknown as { reload: () => void }).reload = reload;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('an active gym leaves nothing to show', async () => {
    const done = service.check();
    http.expectOne(URL).flush({ paused: false, gymName: 'XaloGym', gymContact: null, mygymContact: null });
    await done;
    expect(service.paused()).toBe(false);
    expect(service.status()).toBeNull();
  });

  it('a paused gym exposes its status and contacts', async () => {
    const done = service.check();
    http.expectOne(URL).flush(PAUSED);
    await done;
    expect(service.paused()).toBe(true);
    expect(service.status()?.gymContact?.email).toBe('dueno@xalogym.cl');
    expect(service.status()?.mygymContact?.whatsappNumber).toBe('56964641042');
  });

  it('several checks at once share a single request', async () => {
    const a = service.check();
    const b = service.check();
    http.expectOne(URL).flush(PAUSED); // si hubiera dos, expectOne fallaria
    await Promise.all([a, b]);
    expect(service.paused()).toBe(true);
  });

  it('a failed check (no session, no network) changes nothing', async () => {
    const done = service.check();
    http.expectOne(URL).flush({}, { status: 401, statusText: 'Unauthorized' });
    await done;
    expect(service.paused()).toBe(false);
  });

  it('while paused it keeps checking, and reloads the screen when the gym comes back', async () => {
    const first = service.check();
    http.expectOne(URL).flush(PAUSED);
    await first;

    vi.advanceTimersByTime(45_000);
    http.expectOne(URL).flush({ paused: false, gymName: 'XaloGym', gymContact: null, mygymContact: null });
    await vi.waitFor(() => expect(reload).toHaveBeenCalledTimes(1));
    expect(service.paused()).toBe(false);
  });

  describe('closuresChanged (cierres creados, editados o levantados con la pantalla abierta)', () => {
    const withStamp = (closuresStamp: string | null | undefined) => ({
      paused: false,
      gymName: 'XaloGym',
      gymContact: null,
      mygymContact: null,
      closuresStamp,
    });

    async function checkWith(closuresStamp: string | null | undefined): Promise<void> {
      const done = service.check();
      http.expectOne(URL).flush(withStamp(closuresStamp));
      await done;
    }

    it('the first stamp is only remembered: the screen just loaded its closures', async () => {
      await checkWith('a1');
      expect(service.closuresChanged()).toBe(0);
    });

    it('the same stamp again changes nothing', async () => {
      await checkWith('a1');
      await checkWith('a1');
      expect(service.closuresChanged()).toBe(0);
    });

    it('a different stamp announces the change, once per change', async () => {
      await checkWith('a1');
      await checkWith('b2');
      expect(service.closuresChanged()).toBe(1);
      await checkWith('b2');
      expect(service.closuresChanged()).toBe(1);
      await checkWith('c3');
      expect(service.closuresChanged()).toBe(2);
    });

    it('an old server without a stamp (or a paused gym) never announces anything', async () => {
      await checkWith('a1');
      await checkWith(undefined);
      await checkWith(null);
      expect(service.closuresChanged()).toBe(0);
      await checkWith('a1');
      expect(service.closuresChanged()).toBe(0);
    });
  });

  describe('reservationsChanged (alguien reserva o cancela con la pantalla abierta)', () => {
    async function checkWith(reservationsStamp: string | null | undefined, closuresStamp = 'c1'): Promise<void> {
      const done = service.check();
      http.expectOne(URL).flush({ paused: false, gymName: 'XaloGym', gymContact: null, mygymContact: null, closuresStamp, reservationsStamp });
      await done;
    }

    it('the first stamp is only remembered: the screen just loaded its classes', async () => {
      await checkWith('r1');
      expect(service.reservationsChanged()).toBe(0);
    });

    it('someone booked or cancelled: announces the change once per change', async () => {
      await checkWith('r1');
      await checkWith('r1');
      expect(service.reservationsChanged()).toBe(0);
      await checkWith('r2');
      expect(service.reservationsChanged()).toBe(1);
      await checkWith('r2');
      expect(service.reservationsChanged()).toBe(1);
      await checkWith('r3');
      expect(service.reservationsChanged()).toBe(2);
    });

    it('is independent from closures: only the reservations stamp moves it', async () => {
      await checkWith('r1', 'c1');
      await checkWith('r1', 'c2');
      expect(service.closuresChanged()).toBe(1);
      expect(service.reservationsChanged()).toBe(0);
    });

    it('an old server without the stamp never announces anything', async () => {
      await checkWith('r1');
      await checkWith(undefined);
      await checkWith(null);
      expect(service.reservationsChanged()).toBe(0);
    });
  });

  describe('watch (pantalla abierta con el gimnasio activo)', () => {
    const ACTIVE = { paused: false, gymName: 'XaloGym', gymContact: null, mygymContact: null };
    let visibility: DocumentVisibilityState;

    beforeEach(() => {
      visibility = 'visible';
      vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    });

    afterEach(() => {
      service.unwatch();
      vi.restoreAllMocks();
    });

    it('detects a pause by itself within 60 s, without touching the screen', async () => {
      service.watch();
      vi.advanceTimersByTime(59_000);
      http.expectNone(URL);

      vi.advanceTimersByTime(1_000);
      http.expectOne(URL).flush(PAUSED);
      await vi.waitFor(() => expect(service.paused()).toBe(true));
    });

    it('does not spend calls while the tab is hidden', () => {
      service.watch();
      visibility = 'hidden';
      vi.advanceTimersByTime(180_000);
      http.expectNone(URL);
    });

    it('checks right away when the user comes back to the tab', async () => {
      service.watch();
      visibility = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
      http.expectOne(URL).flush(PAUSED);
      await vi.waitFor(() => expect(service.paused()).toBe(true));
    });

    it('calling watch twice does not double the checks', () => {
      service.watch();
      service.watch();
      vi.advanceTimersByTime(60_000);
      http.expectOne(URL).flush(ACTIVE); // si hubiera dos temporizadores, expectOne fallaria
    });

    it('unwatch stops the checks', () => {
      service.watch();
      service.unwatch();
      vi.advanceTimersByTime(180_000);
      document.dispatchEvent(new Event('visibilitychange'));
      http.expectNone(URL);
    });

    it('once paused, watch leaves the 45 s recheck in charge (no duplicate calls)', async () => {
      service.watch();
      const first = service.check();
      http.expectOne(URL).flush(PAUSED);
      await first;

      vi.advanceTimersByTime(45_000);
      http.expectOne(URL).flush(PAUSED); // el de 45 s
      await vi.waitFor(() => expect(service.paused()).toBe(true));
      vi.advanceTimersByTime(15_000); // llega el tick de 60 s: no consulta porque ya esta en pausa
      http.expectNone(URL);
    });
  });
});
