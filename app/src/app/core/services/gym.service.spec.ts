import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GymService } from './gym.service';
import { Gym, GymBlock } from '../models/gym.model';

describe('GymService', () => {
  let service: GymService;
  let httpMock: HttpTestingController;

  const gym: Gym = {
    id: 1,
    name: 'Gold Gym',
    slug: 'gold-gym',
    active: true,
    maxUsers: 100,
    googleLoginEnabled: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  const block: GymBlock = {
    id: 1,
    gymId: 1,
    label: 'Morning',
    dayOfWeek: 'MONDAY',
    startTime: '08:00',
    endTime: '09:00',
    capacity: 20,
    active: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GymService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() GETs /api/gyms', () => {
    service.list().subscribe((result) => expect(result).toEqual([gym]));
    const req = httpMock.expectOne('/api/gyms');
    expect(req.request.method).toBe('GET');
    req.flush([gym]);
  });

  it('get() GETs /api/gyms/:id', () => {
    service.get(1).subscribe((result) => expect(result).toEqual(gym));
    const req = httpMock.expectOne('/api/gyms/1');
    expect(req.request.method).toBe('GET');
    req.flush(gym);
  });

  it('create() POSTs to /api/gyms', () => {
    const payload = { name: 'Gold Gym', slug: 'gold-gym', maxUsers: 100 };
    service.create(payload).subscribe((result) => expect(result).toEqual(gym));
    const req = httpMock.expectOne('/api/gyms');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(gym);
  });

  it('updateConfig() PUTs to /api/gyms/:id/config', () => {
    const payload = { active: true, maxUsers: 100, googleLoginEnabled: true };
    service.updateConfig(1, payload).subscribe((result) => expect(result).toEqual(gym));
    const req = httpMock.expectOne('/api/gyms/1/config');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(gym);
  });

  it('listBlocks() GETs /api/gyms/:id/blocks', () => {
    service.listBlocks(1).subscribe((result) => expect(result).toEqual([block]));
    const req = httpMock.expectOne('/api/gyms/1/blocks');
    expect(req.request.method).toBe('GET');
    req.flush([block]);
  });

  it('createBlock() POSTs to /api/gyms/:id/blocks', () => {
    const payload = {
      label: 'Morning',
      dayOfWeek: 'MONDAY' as const,
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
    };
    service.createBlock(1, payload).subscribe((result) => expect(result).toEqual(block));
    const req = httpMock.expectOne('/api/gyms/1/blocks');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(block);
  });

  it('updateBlock() PUTs to /api/gyms/:id/blocks/:blockId', () => {
    const payload = {
      label: 'Morning',
      dayOfWeek: 'MONDAY' as const,
      startTime: '08:00',
      endTime: '09:00',
      capacity: 20,
      active: true,
    };
    service.updateBlock(1, 1, payload).subscribe((result) => expect(result).toEqual(block));
    const req = httpMock.expectOne('/api/gyms/1/blocks/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(block);
  });

  it('deleteBlock() DELETEs /api/gyms/:id/blocks/:blockId', () => {
    service.deleteBlock(1, 1).subscribe();
    const req = httpMock.expectOne('/api/gyms/1/blocks/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
