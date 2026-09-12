import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Admin,
  BrandingSuggestion,
  CreateAdminRequest,
  CreateGymBlockRequest,
  CreateGymRequest,
  Gym,
  GymBlock,
  GymConfigUpdateRequest,
  PublicGym,
  UpdateGymBlockRequest,
} from '../models/gym.model';

@Injectable({ providedIn: 'root' })
export class GymService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gyms`;
  private readonly myGymBase = `${environment.apiUrl}/api/gym-admin/gym`;
  private readonly publicGymsBase = `${environment.apiUrl}/api/public/gyms`;

  /** No auth required — powers the public join page for a specific gym. */
  getPublicBySlug(slug: string): Observable<PublicGym> {
    return this.http.get<PublicGym>(`${this.publicGymsBase}/${slug}`);
  }

  list(): Observable<Gym[]> {
    return this.http.get<Gym[]>(this.base);
  }

  get(id: number): Observable<Gym> {
    return this.http.get<Gym>(`${this.base}/${id}`);
  }

  create(payload: CreateGymRequest): Observable<Gym> {
    return this.http.post<Gym>(this.base, payload);
  }

  suggestBranding(name: string): Observable<BrandingSuggestion> {
    return this.http.post<BrandingSuggestion>(`${this.base}/suggest-branding`, { name });
  }

  updateConfig(id: number, payload: GymConfigUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${id}/config`, payload);
  }

  listBlocks(gymId: number): Observable<GymBlock[]> {
    return this.http.get<GymBlock[]>(`${this.base}/${gymId}/blocks`);
  }

  createBlock(gymId: number, payload: CreateGymBlockRequest): Observable<GymBlock> {
    return this.http.post<GymBlock>(`${this.base}/${gymId}/blocks`, payload);
  }

  updateBlock(gymId: number, blockId: number, payload: UpdateGymBlockRequest): Observable<GymBlock> {
    return this.http.put<GymBlock>(`${this.base}/${gymId}/blocks/${blockId}`, payload);
  }

  deleteBlock(gymId: number, blockId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${gymId}/blocks/${blockId}`);
  }

  listAdmins(gymId: number): Observable<Admin[]> {
    return this.http.get<Admin[]>(`${this.base}/${gymId}/admins`);
  }

  addAdmin(gymId: number, payload: CreateAdminRequest): Observable<Admin> {
    return this.http.post<Admin>(`${this.base}/${gymId}/admins`, payload);
  }

  updateAdminStatus(gymId: number, userId: number, active: boolean): Observable<Admin> {
    return this.http.put<Admin>(`${this.base}/${gymId}/admins/${userId}`, { active });
  }

  /** Scoped to the logged-in gym owner's own gym (GYM_ADMIN) — gymId comes from their JWT, not the URL. */
  getMine(): Observable<Gym> {
    return this.http.get<Gym>(this.myGymBase);
  }

  listMyBlocks(): Observable<GymBlock[]> {
    return this.http.get<GymBlock[]>(`${this.myGymBase}/blocks`);
  }

  createMyBlock(payload: CreateGymBlockRequest): Observable<GymBlock> {
    return this.http.post<GymBlock>(`${this.myGymBase}/blocks`, payload);
  }

  updateMyBlock(blockId: number, payload: UpdateGymBlockRequest): Observable<GymBlock> {
    return this.http.put<GymBlock>(`${this.myGymBase}/blocks/${blockId}`, payload);
  }

  deleteMyBlock(blockId: number): Observable<void> {
    return this.http.delete<void>(`${this.myGymBase}/blocks/${blockId}`);
  }

  updateMyTheme(themeColor: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/theme`, { themeColor });
  }

  updateMyLogo(logo: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/logo`, { logo });
  }
}
