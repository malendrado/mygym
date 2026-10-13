import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwPush } from '@angular/service-worker';
import { BehaviorSubject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { PushService } from './push.service';

const BASE = `${environment.apiUrl}/api/push`;

class FakeSwPush {
  isEnabled = true;
  readonly sub$ = new BehaviorSubject<unknown>(null);
  readonly subscription = this.sub$.asObservable();
  requestSubscription = vi.fn(async () => {
    const fake = { endpoint: 'https://fcm.googleapis.com/x', toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/x', keys: { p256dh: 'P', auth: 'A' } }) };
    this.sub$.next(fake);
    return fake;
  });
  unsubscribe = vi.fn(async () => this.sub$.next(null));
}

function setNotification(permission: NotificationPermission): void {
  vi.stubGlobal('Notification', { permission });
  (window as unknown as Record<string, unknown>)['PushManager'] = class {};
}

describe('PushService', () => {
  let http: HttpTestingController;
  let swPush: FakeSwPush;

  function create(): PushService {
    swPush = new FakeSwPush();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SwPush, useValue: swPush }],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(PushService);
  }

  beforeEach(() => {
    localStorage.clear();
    setNotification('default');
  });

  afterEach(() => {
    http?.verify();
    vi.unstubAllGlobals();
  });

  async function flushKey(key: { publicKey: string } | null): Promise<void> {
    await Promise.resolve();
    const req = http.expectOne(`${BASE}/public-key`);
    if (key) req.flush(key);
    else req.flush(null, { status: 204, statusText: 'No Content' });
  }

  it('is unsupported when the server has no push configured (204)', async () => {
    const service = create();
    const done = service.refresh();
    await flushKey(null);
    await done;
    expect(service.state()).toBe('unsupported');
    expect(service.canOffer()).toBe(false);
  });

  it('is "default" and offered on a non-touch device when it can subscribe', async () => {
    const service = create();
    const done = service.refresh();
    await flushKey({ publicKey: 'KEY' });
    await done;
    expect(service.state()).toBe('default');
    expect(service.canOffer()).toBe(true);
  });

  it('is "denied" without asking the server when the user blocked notifications', async () => {
    setNotification('denied');
    const service = create();
    await service.refresh();
    expect(service.state()).toBe('denied');
    http.expectNone(`${BASE}/public-key`);
  });

  it('is "enabled" when permission was granted and a subscription already exists', async () => {
    setNotification('granted');
    const service = create();
    const existing = { endpoint: 'https://fcm.googleapis.com/x', toJSON: () => ({}) };
    (swPush as FakeSwPush).sub$.next(existing);
    const done = service.refresh();
    await flushKey({ publicKey: 'KEY' });
    await done;
    expect(service.state()).toBe('enabled');
  });

  it('enable() subscribes, registers the device with the server and sends the test notice', async () => {
    const service = create();
    const promise = service.enable();
    await flushKey({ publicKey: 'KEY' });
    await vi.waitFor(() => expect(swPush.requestSubscription).toHaveBeenCalledWith({ serverPublicKey: 'KEY' }));
    const reg = await vi.waitFor(() => http.expectOne(`${BASE}/subscriptions`));
    expect(reg.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/x', keys: { p256dh: 'P', auth: 'A' } });
    reg.flush(null, { status: 201, statusText: 'Created' });
    expect(await promise).toEqual({ status: 'ok' });
    expect(service.state()).toBe('enabled');
    http.expectOne(`${BASE}/test`).flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('enable() reports "denied" when the user refuses the system permission', async () => {
    const service = create();
    swPush.requestSubscription.mockImplementation(async () => {
      setNotification('denied');
      throw new Error('denied');
    });
    const promise = service.enable();
    await flushKey({ publicKey: 'KEY' });
    expect(await promise).toEqual({ status: 'denied' });
    expect(service.state()).toBe('denied');
  });

  it('enable() explains a browser without a push service (AbortError) instead of a generic failure', async () => {
    const service = create();
    swPush.requestSubscription.mockImplementation(async () => {
      const error = new Error('Registration failed - push service error');
      error.name = 'AbortError';
      throw error;
    });
    const promise = service.enable();
    await flushKey({ publicKey: 'KEY' });
    expect(await promise).toEqual({ status: 'error', reason: 'no-push-service', detail: 'AbortError' });
  });

  it('enable() reports a server rejection with its HTTP status', async () => {
    const service = create();
    const promise = service.enable();
    await flushKey({ publicKey: 'KEY' });
    const reg = await vi.waitFor(() => http.expectOne(`${BASE}/subscriptions`));
    reg.flush({ detail: 'invalida' }, { status: 400, statusText: 'Bad Request' });
    expect(await promise).toEqual({ status: 'error', reason: 'server', detail: 'HTTP 400' });
  });

  it('disable() removes the device on the server and in the browser', async () => {
    setNotification('granted');
    const service = create();
    swPush.sub$.next({ endpoint: 'https://fcm.googleapis.com/x', toJSON: () => ({}) });
    const promise = service.disable();
    const del = await vi.waitFor(() => http.expectOne((r) => r.method === 'DELETE' && r.url === `${BASE}/subscriptions`));
    expect(del.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/x' });
    del.flush(null, { status: 204, statusText: 'No Content' });
    await promise;
    expect(swPush.unsubscribe).toHaveBeenCalled();
    expect(service.state()).toBe('default');
  });

  it('syncOwnership() re-registers the existing device so it belongs to whoever is signed in now', async () => {
    setNotification('granted');
    const service = create();
    swPush.sub$.next({ endpoint: 'https://fcm.googleapis.com/x', toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/x', keys: { p256dh: 'P', auth: 'A' } }) });
    const done = service.syncOwnership();
    await flushKey({ publicKey: 'KEY' });
    const reg = await vi.waitFor(() => http.expectOne(`${BASE}/subscriptions`));
    reg.flush(null, { status: 201, statusText: 'Created' });
    await done;
  });

  it('dismiss() hides the offer for 14 days and remembers it', () => {
    const service = create();
    expect(service.dismissed()).toBe(false);
    service.dismiss();
    expect(service.dismissed()).toBe(true);
    const until = Number(localStorage.getItem('mygym.pushPromptDismissedUntil'));
    expect(until - Date.now()).toBeGreaterThan(13 * 24 * 60 * 60 * 1000);
  });
});
