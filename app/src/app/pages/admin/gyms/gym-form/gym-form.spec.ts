import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GymForm } from './gym-form';
import { Gym, GymBlock } from '../../../../core/models/gym.model';

function activatedRouteStub(id: string | null) {
  return {
    snapshot: { paramMap: convertToParamMap(id ? { publicId: id } : {}) },
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

    component['createForm'].setValue({ name: 'Gold Gym', slug: 'gold-gym', maxUsers: 100, ownerName: 'Ana Pérez', ownerEmail: 'ana@gold.cl' });
    component['submitCreate']();

    const req = httpMock.expectOne('/api/gyms');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Gold Gym',
      slug: 'gold-gym',
      maxUsers: 100,
      ownerName: 'Ana Pérez',
      ownerEmail: 'ana@gold.cl',
      themeColor: null,
      logoSvg: null,
    });
    req.flush({ id: 1, publicId: 'abc', name: 'Gold Gym', slug: 'gold-gym' } as Gym);
  });
});

describe('GymForm (edit mode)', () => {
  let httpMock: HttpTestingController;

  const gym: Gym = {
    id: 1,
    publicId: '11111111-1111-4111-8111-111111111111',
    name: 'Gold Gym',
    slug: 'gold-gym',
    active: true,
    maxUsers: 100,
    googleLoginEnabled: false,
    themeColor: null,
    logoSvg: null,
    tagline: null,
    description: null,
    instagramUrl: null,
    whatsappNumber: null,
    bookingWindowMinutes: 60,
    cancellationWindowMinutes: 60,
    waitlistHeadStartMinutes: 0,
    showAttendeesToMembers: false,
    fixedMembersCurrentWeekOnly: false,
    themeMode: 'DARK',
    bankName: null,
    bankAccountType: null,
    bankAccountNumber: null,
    bankHolderRut: null,
    bankHolderName: null,
    bankConfirmationEmail: null,
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
    category: null,
    instructorName: null,
    instructorPhoto: null,
    active: true,
  };

  // jsdom no implementa Element.scrollTo y ion-segment lo llama al montar las pestañas.
  beforeAll(() => {
    Element.prototype.scrollTo ??= () => {};
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: activatedRouteStub(gym.publicId) },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the gym and its blocks, and prefills the config form', () => {
    const fixture = TestBed.createComponent(GymForm);
    fixture.detectChanges();

    httpMock.expectOne(`/api/gyms/by-public-id/${gym.publicId}`).flush(gym);
    httpMock.expectOne('/api/gyms/1/blocks').flush([block]);
    // Al resolver el gym el componente pide además los profesores; no es lo que se prueba acá.
    httpMock.match((r) => r.url !== '/api/gyms/1/blocks').forEach((r) => {
      // Las listas de socios vienen paginadas; el resto de los listados son arreglos.
      r.flush(r.request.url.includes('/members') ? { items: [], page: 0, size: 20, totalElements: 0, hasNext: false } : []);
    });

    const component = fixture.componentInstance;
    expect(component['gymName']()).toBe('Gold Gym');
    expect(component['configForm'].value).toEqual({ active: true, maxUsers: 100, googleLoginEnabled: false, logoSvg: '' });
    expect(component['blocks']()).toEqual([block]);
  });
});
