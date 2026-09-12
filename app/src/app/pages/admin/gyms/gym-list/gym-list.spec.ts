import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GymList } from './gym-list';
import { Gym } from '../../../../core/models/gym.model';

describe('GymList', () => {
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders the gyms returned by the API', () => {
    const fixture = TestBed.createComponent(GymList);
    fixture.detectChanges();

    httpMock.expectOne('/api/gyms').flush([gym]);
    fixture.detectChanges();

    expect(fixture.componentInstance['gyms']()).toEqual([gym]);
    expect(fixture.componentInstance['status']()).toBe('loaded');
  });

  it('sets an error status when the request fails', () => {
    const fixture = TestBed.createComponent(GymList);
    fixture.detectChanges();

    httpMock.expectOne('/api/gyms').flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(fixture.componentInstance['status']()).toBe('error');
  });
});
