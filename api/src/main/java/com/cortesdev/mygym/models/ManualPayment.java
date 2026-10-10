package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro de un pago que el admin marcó A MANO (transferencia/efectivo fuera de Flow) — ver
 *  GymService.simulatePlanPayment. Antes ese método no dejaba ningún rastro del pago, solo
 *  actualizaba app_user.planId/paidAt; esto guarda el detalle (monto, banco, quién lo registró)
 *  para los informes del admin. No reemplaza a Payment (que es solo Flow.cl). */
@Entity
@Table(name = "manual_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private Long memberId;

    private Long planId;

    private Integer amountClp;

    /** Nombre del banco (ver CHILE_BANKS en el frontend) o el texto libre que el admin escribió
     *  si eligió "Otro". */
    private String bank;

    /** Null si el admin que lo registró fue borrado después — nunca se pierde la fila por eso. */
    private Long registeredByUserId;

    private Instant paidAt;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
