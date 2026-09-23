import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Admin,
  AnalyticsSummary,
  BankTransferInfo,
  BankTransferUpdateRequest,
  BlockOccurrenceAttendees,
  BrandingSuggestion,
  CheckoutResponse,
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
  ThemeUpdateRequest,
  TrackedPage,
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
  private readonly publicAnalyticsBase = `${environment.apiUrl}/api/public/analytics`;
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

  /** Beacon fire-and-forget de "visita real" — nunca debe interrumpir la página que lo llama,
   *  por eso maneja su propia suscripción acá en vez de devolver el Observable al caller. */
  recordVisit(page: TrackedPage, gymSlug?: string): void {
    this.http.post(`${this.publicAnalyticsBase}/visit`, { page, gymSlug: gymSlug ?? null }).subscribe({
      error: () => {
        /* Best-effort: si falla, no pasa nada — la página sigue funcionando igual. */
      },
    });
  }

  /** SUPER_ADMIN only — panel de Visitas en /admin/gyms. */
  getAnalyticsSummary(): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(`${this.base}/analytics/summary`);
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

  /** Datos bancarios de SU gym para pagar por transferencia (alternativa a Flow) — nunca
   *  expuestos en la página pública de alta, solo acá para el socio autenticado. */
  getMyBankTransferInfo(): Observable<BankTransferInfo> {
    return this.http.get<BankTransferInfo>(`${this.meBase}/gym/bank-transfer`);
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

  /** Batch para Historial (dueño del gym) — una sola llamada por semana en vez de una por
   *  cada bloque×día, ver GymAdminController.myHistoryAttendees. */
  getMyHistoryAttendees(from: string, to: string): Observable<BlockOccurrenceAttendees[]> {
    return this.http.get<BlockOccurrenceAttendees[]>(`${this.myGymBase}/history-attendees`, { params: { from, to } });
  }

  /** Contraparte SUPER_ADMIN de getMyHistoryAttendees — mismo backend, gymId por path. */
  getHistoryAttendees(gymId: number, from: string, to: string): Observable<BlockOccurrenceAttendees[]> {
    return this.http.get<BlockOccurrenceAttendees[]>(`${this.base}/${gymId}/history-attendees`, { params: { from, to } });
  }

  /** Buscador de reservas futuras por nombre de socio (dueño del gym) — mismo formato de
   *  respuesta que getMyHistoryAttendees, ver ReservationService.searchUpcomingReservations. */
  searchMyReservations(query: string): Observable<BlockOccurrenceAttendees[]> {
    return this.http.get<BlockOccurrenceAttendees[]>(`${this.myGymBase}/reservations/search`, { params: { q: query } });
  }

  /** Contraparte SUPER_ADMIN de searchMyReservations — mismo backend, gymId por path. */
  searchReservations(gymId: number, query: string): Observable<BlockOccurrenceAttendees[]> {
    return this.http.get<BlockOccurrenceAttendees[]>(`${this.base}/${gymId}/reservations/search`, { params: { q: query } });
  }

  /** Vía de urgencia del admin: cancela la reserva de CUALQUIER socio del gym, sin la ventana
   *  de horas que le aplica a la auto-cancelación del socio. */
  cancelReservation(reservationId: number): Observable<void> {
    return this.http.delete<void>(`${this.myGymBase}/reservations/${reservationId}`);
  }

  /** Contraparte SUPER_ADMIN de cancelReservation — mismo backend, gymId por path. */
  cancelReservationForGym(gymId: number, reservationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${gymId}/reservations/${reservationId}`);
  }

  /**
   * Arranca el pago real con Flow.cl — pago manual mes a mes, sin tarjeta
   * guardada ni cobro automático (mygym no se hace cargo de guardar
   * tarjetas de nadie). Devuelve la URL a la que hay que redirigir el
   * navegador completo para que el socio pague. La confirmación real llega
   * después por webhook, nunca acá.
   */
  startCheckout(planId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.meBase}/plans/${planId}/checkout`, {});
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

  // Acceso de solo-lectura a la demo comercial (Role.DEMO_ADMIN) — activar/desactivar reusa
  // updateAdminStatus de arriba (mismo endpoint sirve para ambos tipos de admin en el backend).
  listDemoAdmins(gymId: number): Observable<Admin[]> {
    return this.http.get<Admin[]>(`${this.base}/${gymId}/demo-admins`);
  }

  addDemoAdmin(gymId: number, payload: CreateAdminRequest): Observable<Admin> {
    return this.http.post<Admin>(`${this.base}/${gymId}/demo-admins`, payload);
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

  updateTheme(gymId: number, request: ThemeUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${gymId}/theme`, request);
  }

  updateIdentity(gymId: number, payload: GymIdentityUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${gymId}/identity`, payload);
  }

  updateBankTransfer(gymId: number, payload: BankTransferUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.base}/${gymId}/bank-transfer`, payload);
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

  updateMyTheme(request: ThemeUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/theme`, request);
  }

  updateMyLogo(logo: string): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/logo`, { logo });
  }

  updateMyIdentity(payload: GymIdentityUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/identity`, payload);
  }

  updateMyBankTransfer(payload: BankTransferUpdateRequest): Observable<Gym> {
    return this.http.put<Gym>(`${this.myGymBase}/bank-transfer`, payload);
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
