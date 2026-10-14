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
});
