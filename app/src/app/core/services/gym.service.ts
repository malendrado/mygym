import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Admin,
  BrandingSuggestion,
  CreateAdminRequest,
  CreateGymBlockRequest,
  CreateGymPhotoRequest,
  CreateGymPlanRequest,
  CreateGymRequest,
  Gym,
  GymBlock,
  GymConfigUpdateRequest,
  GymIdentityUpdateRequest,
  GymPhoto,
  GymPlan,
  MemberPlan,
  PublicGym,
  UpdateGymBlockRequest,
  UpdateGymPlanRequest,
} from '../models/gym.model';
import { Attendee, AttendeeSummary, Member } from '../models/member.model';

@Injectable({ providedIn: 'root' })
export class GymService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/gyms`;
  private readonly myGymBase = `${environment.apiUrl}/api/gym-admin/gym`;
  private readonly publicGymsBase = `${environment.apiUrl}/api/public/gyms`;
  private readonly meBase = `${environment.apiUrl}/api/me`;

  /** No auth required — powers the public join page for a specific gym. */
  getPublicBySlug(slug: string): Observable<PublicGym> {
    return this.http.get<PublicGym>(`${this.publicGymsBase}/${slug}`);
  }

  /** No auth required — planes activos de un gym para mostrar antes del alta en /j/{slug}. */
  getPublicPlansBySlug(slug: string): Observable<MemberPlan[]> {
    return this.http.get<MemberPlan[]>(`${this.publicGymsBase}/${slug}/plans`);
  }

  /** No auth required — fotos de las instalaciones para mostrar antes del alta en /j/{slug}. */
  getPublicPhotosBySlug(slug: string): Observable<GymPhoto[]> {
    return this.http.get<GymPhoto[]>(`${this.publicGymsBase}/${slug}/photos`);
  }

  /** Branding for the logged-in member's own gym — gymId comes from their JWT, not a param. */
  getMyMemberGym(): Observable<PublicGym> {
    return this.http.get<PublicGym>(`${this.meBase}/gym`);
  }

  /** Planes activos configurados por el admin del gym del socio logueado. */
  getMyMemberPlans(): Observable<MemberPlan[]> {
    return this.http.get<MemberPlan[]>(`${this.meBase}/plans`);
  }

  /** Fotos de las instalaciones del gym del socio logueado. */
  getMyMemberPhotos(): Observable<GymPhoto[]> {
    return this.http.get<GymPhoto[]>(`${this.meBase}/gym/photos`);
  }

  /** Plan/pago real del socio logueado (planId/paidAt ya persistidos, no el mock local de member.ts). */
  getMyMembership(): Observable<Member> {
    return this.http.get<Member>(`${this.meBase}/membership`);
  }

  /** Quién más reservó una clase puntual — vista reducida (sin email) para el propio socio. */
  getMyBlockAttendees(blockId: number, classDate: string): Observable<AttendeeSummary[]> {
    return this.http.get<AttendeeSummary[]>(`${this.meBase}/gym-blocks/${blockId}/occurrences/${classDate}/attendees`);
  }

  /** Vista completa (con email) para el dueño del gimnasio. */
  getMyGymBlockAttendees(blockId: number, classDate: string): Observable<Attendee[]> {
    return this.http.get<Attendee[]>(`${this.myGymBase}/blocks/${blockId}/occurrences/${classDate}/attendees`);
  }

  /** Vista completa (con email) para el super-admin, un gimnasio puntual. */
  getBlockAttendees(gymId: number, blockId: number, classDate: string): Observable<Attendee[]> {
    return this.http.get<Attendee[]>(`${this.base}/${gymId}/blocks/${blockId}/occurrences/${classDate}/attendees`);
  }

  /**
   * Todavía no existe el pago real (Flow.cl, pendiente) — este endpoint solo
   * dispara los emails de "pago confirmado" a socio y admin, sin crear
   * ninguna suscripción real. El resto del "pago" sigue simulado en el
   * frontend (member.ts selectPlan()).
   */
  simulateMyPlanPayment(planId: number): Observable<void> {
    return this.http.post<void>(`${this.meBase}/plans/${planId}/simulate-payment`, {});
  }

  /**
   * Mismo caso que simulateMyPlanPayment: el frontend calcula cuándo se
   * cumplió el mes desde el pago (member.ts, membershipExpired) y dispara
   * esto una vez — solo manda los emails de aviso a socio y admin.
   */
  simulateMyPlanExpiry(planId: number): Observable<void> {
    return this.http.post<void>(`${this.meBase}/plans/${planId}/simulate-expiry`, {});
  }

  list(): Observable<Gym[]> {
    return this.http.get<Gym[]>(this.base);
  }

  get(id: number): Observable<Gym> {
    return this.http.get<Gym>(`${this.base}/${id}`);
  }

  /** Resuelve el UUID opaco de la URL (/admin/gyms/:publicId) al gym completo. */
  getByPublicId(publicId: string): Observable<Gym> {
    return this.http.get<Gym>(`${this.base}/by-public-id/${publicId}`);
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

  /** Contraparte SUPER_ADMIN de listMyPlans/createMyPlan/etc. — mismo backend, gymId por path en vez de por JWT. */
  listPlans(gymId: number): Observable<GymPlan[]> {
    return this.http.get<GymPlan[]>(`${this.base}/${gymId}/plans`);
  }

  createPlan(gymId: number, payload: CreateGymPlanRequest): Observable<GymPlan> {
    return this.http.post<GymPlan>(`${this.base}/${gymId}/plans`, payload);
  }

  updatePlan(gymId: number, planId: number, payload: UpdateGymPlanRequest): Observable<GymPlan> {
    return this.http.put<GymPlan>(`${this.base}/${gymId}/plans/${planId}`, payload);
  }

  deletePlan(gymId: number, planId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${gymId}/plans/${planId}`);
  }

  updateTheme(gymId: number, themeColor: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${gymId}/theme`, { themeColor });
  }

  updateIdentity(gymId: number, payload: GymIdentityUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${gymId}/identity`, payload);
  }

  listPhotos(gymId: number): Observable<GymPhoto[]> {
    return this.http.get<GymPhoto[]>(`${this.base}/${gymId}/photos`);
  }

  createPhoto(gymId: number, payload: CreateGymPhotoRequest): Observable<GymPhoto> {
    return this.http.post<GymPhoto>(`${this.base}/${gymId}/photos`, payload);
  }

  deletePhoto(gymId: number, photoId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${gymId}/photos/${photoId}`);
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

  listMyPlans(): Observable<GymPlan[]> {
    return this.http.get<GymPlan[]>(`${this.myGymBase}/plans`);
  }

  createMyPlan(payload: CreateGymPlanRequest): Observable<GymPlan> {
    return this.http.post<GymPlan>(`${this.myGymBase}/plans`, payload);
  }

  updateMyPlan(planId: number, payload: UpdateGymPlanRequest): Observable<GymPlan> {
    return this.http.put<GymPlan>(`${this.myGymBase}/plans/${planId}`, payload);
  }

  deleteMyPlan(planId: number): Observable<void> {
    return this.http.delete<void>(`${this.myGymBase}/plans/${planId}`);
  }

  updateMyTheme(themeColor: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/theme`, { themeColor });
  }

  updateMyLogo(logo: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/logo`, { logo });
  }

  updateMyIdentity(payload: GymIdentityUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/identity`, payload);
  }

  listMyPhotos(): Observable<GymPhoto[]> {
    return this.http.get<GymPhoto[]>(`${this.myGymBase}/photos`);
  }

  createMyPhoto(payload: CreateGymPhotoRequest): Observable<GymPhoto> {
    return this.http.post<GymPhoto>(`${this.myGymBase}/photos`, payload);
  }

  deleteMyPhoto(photoId: number): Observable<void> {
    return this.http.delete<void>(`${this.myGymBase}/photos/${photoId}`);
  }
}
