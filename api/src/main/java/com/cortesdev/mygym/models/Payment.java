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

/** Historial/auditoría de pagos reales con Flow.cl — pago manual mes a mes, sin tarjeta guardada
 *  ni cobro automático (decisión explícita del usuario: no nos hacemos cargo de guardar tarjetas).
 *  No existía ningún registro de pagos hasta esta feature, solo el último app_user.paid_at. Una
 *  fila por intento de pago (PENDING al crear el checkout, PAID/REJECTED cuando confirma el
 *  webhook). */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long planId;

    /** Nuestra referencia propia (commerceOrder de Flow) — se genera al crear el checkout. */
    private String commerceOrder;

    private String flowToken;

    private String flowOrder;

    private Integer amountClp;

    /** PENDING | PAID | REJECTED */
    private String status;

    private Instant paidAt;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
