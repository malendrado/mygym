import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GymList } from './gym-list';
import { GymSummary } from '../../../../core/models/gym.model';

describe('GymList', () => {
  let httpMock: HttpTestingController;

  const gym: GymSummary = {
    id: 1,
    publicId: 'b6f0c1de-0000-4000-8000-000000000001',
    name: 'Gold Gym',
    slug: 'gold-gym',
    active: true,
    maxUsers: 100,
    themeColor: null,
    logoSvg: null,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ionViewWillEnter lo dispara el router de Ionic; en el test se llama a mano.
  const enter = (fixture: ReturnType<typeof TestBed.createComponent<GymList>>) => {
    fixture.detectChanges();
    fixture.componentInstance.ionViewWillEnter();
  };

  it('renders the first page of gyms returned by the API', () => {
    const fixture = TestBed.createComponent(GymList);
    enter(fixture);

    httpMock.expectOne('/api/gyms/stats').flush({ total: 1, active: 1, totalCapacity: 100, branded: 0 });
    httpMock.expectOne('/api/gyms/analytics/summary').flush({ brochure: {}, landing: {}, joinTotal: {}, byGym: [] });
    httpMock
      .expectOne((r) => r.url === '/api/gyms')
      .flush({ items: [gym], page: 0, size: 12, totalElements: 1, hasNext: false });
    fixture.detectChanges();

    expect(fixture.componentInstance['gyms']()).toEqual([gym]);
    expect(fixture.componentInstance['gymList'].loaded()).toBe(true);
    expect(fixture.componentInstance['totalGyms']()).toBe(1);
  });

  it('sets an error state when the request fails', () => {
    const fixture = TestBed.createComponent(GymList);
    enter(fixture);

    httpMock.expectOne('/api/gyms/stats').flush({}, { status: 500, statusText: 'Internal Server Error' });
    httpMock.expectOne('/api/gyms/analytics/summary').flush({}, { status: 500, statusText: 'Internal Server Error' });
    httpMock
      .expectOne((r) => r.url === '/api/gyms')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(fixture.componentInstance['gymList'].error()).toBe(true);
  });
});
