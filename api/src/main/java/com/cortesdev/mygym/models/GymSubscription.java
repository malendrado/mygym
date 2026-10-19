package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
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

/**
 * Suscripcion de un gimnasio a mygym. El periodo se calcula igual que el de un socio (ver {@code MembershipPeriods}): un
 * ancla y los meses seguidos pagados; el vencimiento es {@code anchorDate + paidPeriods meses}, siempre contado desde el ancla
 * para que el dia de corte no se corra (31 ene, 28 feb, 31 mar).
 */
@Entity
@Table(name = "gym_subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymSubscription {

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String ACTIVE = "ACTIVE";
    public static final String GRACE = "GRACE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String EXEMPT = "EXEMPT";

    @Id
    private Long gymId;

    private Long planId;

    /** Precio neto propio del gimnasio. Null = el del plan. */
    private Integer priceOverrideClp;

    /** Cupo de socios propio del gimnasio. Null = el del plan. */
    private Integer maxUsersOverride;

    /** Fecha de inicio: define el dia de corte. Null mientras el gimnasio no se activa. */
    private LocalDate anchorDate;

    private int paidPeriods;

    private Long pendingPlanId;

    private Integer pendingPriceOverrideClp;

    private LocalDate pendingPlanFrom;

    private boolean exempt;

    private String billingStatus;

    private Instant suspendedAt;

    private String billingRut;

    private String billingName;

    private String billingGiro;

    private String billingAddress;

    private String billingEmail;

    private LocalDate lastNoticeOn;

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
