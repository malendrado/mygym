/** Espejo de GymSubscriptionDtos (backend): suscripción de un gimnasio a mygym y catálogo de planes de mygym. */

export interface MygymPlan {
  id: number;
  code: string;
  name: string;
  clientsLabel: string;
  /** Neto mensual en CLP, sin IVA. Null = "A medida". */
  priceClp: number | null;
  /** Cupo de socios del plan. Null = sin tope propio (se define por gimnasio). */
  maxUsers: number | null;
  maxProfesores: number;
  maxAdmins: number;
  features: string[];
  highlighted: boolean;
  sortOrder: number;
  active: boolean;
}

export type MygymPlanRequest = Omit<MygymPlan, 'id'>;

export type BillingStatus = 'NOT_STARTED' | 'ACTIVE' | 'GRACE' | 'SUSPENDED' | 'EXEMPT';
export type PaymentMethod = 'FLOW' | 'TRANSFER' | 'MANUAL';
export type PaymentStatus = 'PENDING' | 'REPORTED' | 'PAID' | 'REJECTED' | 'VOIDED';

export interface SubscriptionPayment {
  id: number;
  method: PaymentMethod;
  status: PaymentStatus;
  amountClp: number;
  netClp: number;
  ivaClp: number;
  flowFeeClp: number;
  periodStart: string | null;
  periodEnd: string | null;
  createdAt: string;
  confirmedAt: string | null;
  transferDate: string | null;
  transferBank: string | null;
  transferOperation: string | null;
  note: string | null;
}

/** A quién emite mygym la factura: una empresa, o una persona natural con inicio de actividades. */
export type BillingKind = 'COMPANY' | 'PERSON';

export interface BillingInfo {
  kind: BillingKind;
  rut: string | null;
  name: string | null;
  giro: string | null;
  address: string | null;
  email: string | null;
}

export interface BankTransferData {
  holder: string;
  rut: string;
  bank: string;
  accountType: string;
  accountNumber: string;
  email: string;
}

export interface GymSubscription {
  gymId: number;
  status: BillingStatus;
  exempt: boolean;
  planId: number | null;
  planName: string | null;
  clientsLabel: string | null;
  netClp: number | null;
  ivaClp: number | null;
  totalClp: number | null;
  flowFeeClp: number | null;
  flowTotalClp: number | null;
  maxUsers: number | null;
  priceOverrideClp: number | null;
  maxUsersOverride: number | null;
  anchorDate: string | null;
  dueDate: string | null;
  /** Primer día en que queda suspendido si no paga. */
  suspensionDate: string | null;
  daysToDue: number | null;
  canRenew: boolean;
  transferPending: boolean;
  pendingPlanId: number | null;
  pendingPlanName: string | null;
  pendingPlanFrom: string | null;
  /** Cupo de socios que regirá con el plan pendiente. Null si no hay cambio pendiente. */
  pendingMaxUsers?: number | null;
  billing: BillingInfo;
  bank: BankTransferData;
  payments: SubscriptionPayment[];
  /** Plan al que corresponde el monto mostrado (el del próximo pago): distinto de planName cuando el cambio de plan ya rige en la próxima renovación. */
  chargePlanName?: string | null;
  chargeClientsLabel?: string | null;
  /** Neto mensual del plan de hoy (cuando hay cambio de plan en curso, para compararlo con netClp). */
  currentNetClp?: number | null;
  /** Firma del contrato de servicios (se exige antes de pagar). */
  contract?: ContractSummary;
}

export type SignerCapacity = 'PERSON_OWNER' | 'LEGAL_REP' | 'ATTORNEY';

export interface ContractSummary {
  required: boolean;
  signed: boolean;
  version: string;
  signedAt: string | null;
  signerName: string | null;
  signerCapacity: SignerCapacity | null;
  /** Plan Escala facturado a una empresa: mygym pide además el certificado de vigencia por correo. */
  certificateNeeded: boolean;
}

/** Lo que muestra la pantalla de firma. `blockedReason` no nulo = todavía no se puede firmar. */
export interface ContractView {
  version: string;
  signed: boolean;
  signedAt: string | null;
  signerName: string | null;
  signerCapacity: SignerCapacity | null;
  text: string | null;
  blockedReason: string | null;
  maskedEmail: string | null;
  certificateNeeded: boolean;
}

export interface ContractCodeRequest {
  signerName: string;
  signerRut: string;
  capacity: SignerCapacity;
  adult: boolean;
  powers: boolean;
}

export interface ActivateSubscriptionRequest {
  planId: number;
  startDate: string | null;
  priceOverrideClp: number | null;
  maxUsersOverride: number | null;
  method: PaymentMethod;
  operation: string | null;
  note: string | null;
}

export interface UpdateSubscriptionRequest {
  priceOverrideClp: number | null;
  maxUsersOverride: number | null;
  anchorDate: string | null;
  exempt: boolean | null;
}

export interface ReportTransferRequest {
  transferDate: string;
  bank: string;
  operation: string | null;
}
