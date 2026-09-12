import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GymForm } from './gym-form';
import { Gym, GymBlock } from '../../../../core/models/gym.model';

function activatedRouteStub(id: string | null) {
  return {
    snapshot: { paramMap: convertToParamMap(id ? { id } : {}) },
  };
}

describe('GymForm (creation mode)', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'admin/gyms/:id', children: [] }]),
        { provide: ActivatedRoute, useValue: activatedRouteStub(null) },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('keeps the create form invalid when fields are empty', () => {
    const fixture = TestBed.createComponent(GymForm);
    fixture.detectChanges();
    expect(fixture.componentInstance['createForm'].invalid).toBe(true);
  });

  it('calls GymService.create with the exact payload on valid submit', () => {
    const fixture = TestBed.createComponent(GymForm);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component['createForm'].setValue({ name: 'Gold Gym', slug: 'gold-gym', maxUsers: 100 });
    component['submitCreate']();

    const req = httpMock.expectOne('/api/gyms');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Gold Gym', slug: 'gold-gym', maxUsers: 100 });
    req.flush({ id: 1, name: 'Gold Gym', slug: 'gold-gym', active: true, maxUsers: 100, googleLoginEnabled: false, createdAt: '', updatedAt: '' } as Gym);
  });
});

describe('GymForm (edit mode)', () => {
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: activatedRouteStub('1') },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the gym and its blocks, and prefills the config form', () => {
    const fixture = TestBed.createComponent(GymForm);
    fixture.detectChanges();

    httpMock.expectOne('/api/gyms/1').flush(gym);
    httpMock.expectOne('/api/gyms/1/blocks').flush([block]);

    const component = fixture.componentInstance;
    expect(component['gymName']()).toBe('Gold Gym');
    expect(component['configForm'].value).toEqual({ active: true, maxUsers: 100, googleLoginEnabled: false });
    expect(component['blocks']()).toEqual([block]);
  });
});
