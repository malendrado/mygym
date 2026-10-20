package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos de la API de suscripcion de un gimnasio a mygym y del catalogo de planes de mygym. */
public final class GymSubscriptionDtos {

    private GymSubscriptionDtos() {}

    // ---- Catalogo de planes -------------------------------------------------------------------------------------------

    public record PlanResponse(
            Long id,
            String code,
            String name,
            String clientsLabel,
            Integer priceClp,
            Integer maxUsers,
            int maxProfesores,
            int maxAdmins,
            List<String> features,
            boolean highlighted,
            int sortOrder,
            boolean active) {}

    public record PlanRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 80) String clientsLabel,
            @Min(1) @Max(100_000_000) Integer priceClp,
            @Min(1) @Max(1_000_000) Integer maxUsers,
            @Min(1) @Max(1000) int maxProfesores,
            @Min(1) @Max(1000) int maxAdmins,
            @NotNull @Size(max = 12) List<@NotBlank @Size(max = 120) String> features,
            boolean highlighted,
            @Min(0) @Max(1000) int sortOrder,
            boolean active) {}

    // ---- Suscripcion --------------------------------------------------------------------------------------------------

    /** `kind`: COMPANY (empresa) o PERSON (persona natural con inicio de actividades). */
    public record BillingInfo(String kind, String rut, String name, String giro, String address, String email) {}

    public record BankTransferInfo(
            String holder, String rut, String bank, String accountType, String accountNumber, String email) {}

    public record PaymentItem(
            Long id,
            String method,
            String status,
            int amountClp,
            int netClp,
            int ivaClp,
            int flowFeeClp,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant createdAt,
            Instant confirmedAt,
            LocalDate transferDate,
            String transferBank,
            String transferOperation,
            String note) {}

    /** Estado completo de la suscripcion: lo ve el administrador del gimnasio y el super-admin. */
    public record SubscriptionResponse(
            Long gymId,
            /** NOT_STARTED | ACTIVE | GRACE | SUSPENDED | EXEMPT */
            String status,
            boolean exempt,
            Long planId,
            String planName,
            String clientsLabel,
            /** Monto mensual neto vigente (null mientras sea "a medida" sin precio). */
            Integer netClp,
            Integer ivaClp,
            Integer totalClp,
            Integer flowFeeClp,
            Integer flowTotalClp,
            Integer maxUsers,
            Integer priceOverrideClp,
            Integer maxUsersOverride,
            LocalDate anchorDate,
            LocalDate dueDate,
            /** Primer dia en que queda suspendido si no paga. */
            LocalDate suspensionDate,
            Long daysToDue,
            boolean canRenew,
            boolean transferPending,
            Long pendingPlanId,
            String pendingPlanName,
            LocalDate pendingPlanFrom,
            BillingInfo billing,
            BankTransferInfo bank,
            List<PaymentItem> payments,
            /** Plan al que corresponde el monto de arriba (el del proximo pago): distinto de planName cuando hay un cambio de plan ya en curso. */
            String chargePlanName,
            String chargeClientsLabel,
            /** Monto mensual neto del plan de HOY (distinto de netClp cuando ya rige un cambio de plan en la proxima renovacion). */
            Integer currentNetClp) {}

    public record ActivateRequest(
            @NotNull Long planId,
            LocalDate startDate,
            @Min(1) @Max(100_000_000) Integer priceOverrideClp,
            @Min(1) @Max(1_000_000) Integer maxUsersOverride,
            /** Como pago el primer mes: FLOW | TRANSFER | MANUAL. */
            @NotBlank String method,
            @Size(max = 60) String operation,
            @Size(max = 240) String note) {}

    public record SubscriptionUpdateRequest(
            @Min(1) @Max(100_000_000) Integer priceOverrideClp,
            @Min(1) @Max(1_000_000) Integer maxUsersOverride,
            LocalDate anchorDate,
            Boolean exempt) {}

    public record ChangePlanRequest(@NotNull Long planId, @Min(1) @Max(100_000_000) Integer priceOverrideClp) {}

    /** Pago registrado a mano por el super-admin (sin aviso previo del gimnasio). */
    public record ManualPaymentRequest(
            @NotBlank String method,
            @Size(max = 60) String operation,
            @Size(max = 240) String note) {}

    /** "Ya transferi", que envia el administrador del gimnasio. */
    public record ReportTransferRequest(
            @NotNull LocalDate transferDate,
            @NotBlank @Size(max = 80) String bank,
            @Size(max = 60) String operation) {}

    public record RejectTransferRequest(@Size(max = 240) String reason) {}

    public record BillingInfoRequest(
            @Pattern(regexp = "COMPANY|PERSON") String kind,
            @Size(max = 20) String rut,
            @Size(max = 160) String name,
            @Size(max = 160) String giro,
            @Size(max = 240) String address,
            @Email @Size(max = 160) String email) {}

    public record CheckoutResponse(String url) {}
}
