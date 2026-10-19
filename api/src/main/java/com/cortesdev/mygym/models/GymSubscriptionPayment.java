package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pago de un gimnasio a mygym (Flow, transferencia o marcado a mano). No confundir con {@link Payment} (socio -> gimnasio). */
@Entity
@Table(name = "gym_subscription_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymSubscriptionPayment {

    public static final String FLOW = "FLOW";
    public static final String TRANSFER = "TRANSFER";
    public static final String MANUAL = "MANUAL";

    public static final String PENDING = "PENDING";
    public static final String REPORTED = "REPORTED";
    public static final String PAID = "PAID";
    public static final String REJECTED = "REJECTED";
    public static final String VOIDED = "VOIDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private String method;

    private String status;

    private int netClp;

    private int ivaClp;

    private int flowFeeClp;

    private int amountClp;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String commerceOrder;

    private String flowToken;

    private String flowOrder;

    private LocalDate transferDate;

    private String transferBank;

    private String transferOperation;

    private Long reportedBy;

    private Instant reportedAt;

    private Long confirmedBy;

    private Instant confirmedAt;

    private String note;

    /** Estado de la suscripcion antes de aplicar este pago (para anularlo). */
    private LocalDate prevAnchorDate;

    private Integer prevPaidPeriods;

    private Instant voidedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
