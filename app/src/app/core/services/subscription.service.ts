import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ActivateSubscriptionRequest,
  BillingInfo,
  GymSubscription,
  MygymPlan,
  MygymPlanRequest,
  PaymentMethod,
  ReportTransferRequest,
  UpdateSubscriptionRequest,
} from '../models/subscription.model';

/**
 * Suscripción de los gimnasios a mygym y catálogo de planes de mygym. `*Mine` es el propio gimnasio del administrador (el
 * servidor lo resuelve por la sesión); el resto es del super-admin y lleva el id del gimnasio.
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  // ---- Planes de mygym

  /** Público: lo usan la landing y el FAQ. */
  listPublicPlans(): Observable<MygymPlan[]> {
    return this.http.get<MygymPlan[]>(`${this.api}/api/public/mygym-plans`);
  }

  listPlans(): Observable<MygymPlan[]> {
    return this.http.get<MygymPlan[]>(`${this.api}/api/mygym-plans`);
  }

  createPlan(request: MygymPlanRequest & { code: string }): Observable<MygymPlan> {
    return this.http.post<MygymPlan>(`${this.api}/api/mygym-plans`, request);
  }

  updatePlan(id: number, request: MygymPlanRequest & { code: string }): Observable<MygymPlan> {
    return this.http.put<MygymPlan>(`${this.api}/api/mygym-plans/${id}`, request);
  }

  deletePlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/api/mygym-plans/${id}`);
  }

  // ---- Administrador del gimnasio (su propio gimnasio)

  getMine(): Observable<GymSubscription> {
    return this.http.get<GymSubscription>(`${this.api}/api/gym-admin/subscription`);
  }

  /** Arranca el pago con Flow: devuelve la URL a la que hay que llevar al administrador. */
  checkout(): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(`${this.api}/api/gym-admin/subscription/checkout`, {});
  }

  reportTransfer(request: ReportTransferRequest): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.api}/api/gym-admin/subscription/transfer-report`, request);
  }

  updateBillingMine(info: BillingInfo): Observable<GymSubscription> {
    return this.http.put<GymSubscription>(`${this.api}/api/gym-admin/subscription/billing`, info);
  }

  // ---- Super-admin (cualquier gimnasio)

  get(gymId: number): Observable<GymSubscription> {
    return this.http.get<GymSubscription>(this.base(gymId));
  }

  activate(gymId: number, request: ActivateSubscriptionRequest): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.base(gymId)}/activate`, request);
  }

  update(gymId: number, request: UpdateSubscriptionRequest): Observable<GymSubscription> {
    return this.http.put<GymSubscription>(this.base(gymId), request);
  }

  changePlan(gymId: number, planId: number, priceOverrideClp: number | null): Observable<GymSubscription> {
    return this.http.put<GymSubscription>(`${this.base(gymId)}/plan`, { planId, priceOverrideClp });
  }

  updateBilling(gymId: number, info: BillingInfo): Observable<GymSubscription> {
    return this.http.put<GymSubscription>(`${this.base(gymId)}/billing`, info);
  }

  markPaid(gymId: number, method: PaymentMethod, operation: string | null, note: string | null): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.base(gymId)}/payments`, { method, operation, note });
  }

  confirmTransfer(gymId: number, paymentId: number): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.base(gymId)}/payments/${paymentId}/confirm`, {});
  }

  rejectTransfer(gymId: number, paymentId: number, reason: string | null): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.base(gymId)}/payments/${paymentId}/reject`, { reason });
  }

  voidPayment(gymId: number, paymentId: number): Observable<GymSubscription> {
    return this.http.post<GymSubscription>(`${this.base(gymId)}/payments/${paymentId}/void`, {});
  }

  private base(gymId: number): string {
    return `${this.api}/api/gyms/${gymId}/subscription`;
  }
}
