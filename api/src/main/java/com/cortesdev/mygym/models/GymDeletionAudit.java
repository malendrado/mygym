package com.cortesdev.mygym.models;

import jakarta.persistence.Column;
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

/** Prueba permanente de que un gimnasio desvinculado fue borrado de verdad — nunca se
 *  actualiza ni se borra una fila de acá (ver GymDisconnectionService). Deliberadamente sin
 *  datos personales de socios, solo conteos — el detalle completo se le entrega al dueño del
 *  gym por email, mygym no se queda con una copia. */
@Entity
@Table(name = "gym_deletion_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymDeletionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gymName;

    private String gymSlug;

    /** Emails de los admins del gimnasio al momento de la desvinculación, separados por coma. */
    @Column(columnDefinition = "text")
    private String adminEmails;

    private int memberCount;

    private int reservationCount;

    private int paymentCount;

    private int blockCount;

    private int planCount;

    private int photoCount;

    /** Email del super-admin que ejecutó la desvinculación. */
    private String executedBy;

    private Instant executedAt;

    @PrePersist
    void onCreate() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }
}
