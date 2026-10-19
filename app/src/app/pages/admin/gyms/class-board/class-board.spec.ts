import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { environment } from '../../../../../environments/environment';
import { ClassBoardView } from './class-board';

const API = environment.apiUrl;

function create(gymId: number | null) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ClassBoardView);
  if (gymId !== null) {
    fixture.componentRef.setInput('gymId', gymId);
  }
  fixture.detectChanges();
  return http;
}

describe('ClassBoardView (tablero "En clase")', () => {
  it('as super-admin the FIRST request already goes to the endpoint of that gym (no 403 until retrying)', () => {
    const http = create(5);
    http.expectOne(`${API}/api/gyms/5/class-board`);
    http.expectNone(`${API}/api/gym-admin/class-board`);
  });

  it('as gym admin or teacher it uses the endpoint that resolves the gym from the session', () => {
    const http = create(null);
    http.expectOne(`${API}/api/gym-admin/class-board`);
  });
});
