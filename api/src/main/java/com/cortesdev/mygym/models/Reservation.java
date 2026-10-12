package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymBlockId;

    private Long memberId;

    private LocalDate classDate;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    // Null = no vino todavía. "No show" se calcula al vuelo (BOOKED + clase ya terminó + esto
    // sigue null), no es un status aparte — ver ReservationService.checkIn.
    private Instant checkedInAt;

    /** Cómo se marcó la asistencia: QR de la TV o a mano por un admin/profesor. Null = sin marcar. */
    @Enumerated(EnumType.STRING)
    private CheckinMethod checkinMethod;

    /** Quién marcó o desmarcó a mano (null cuando fue por QR). Solo registro, sin FK. */
    private Long checkedInByUserId;

    /** Null = cancelada por el socio o por el admin a mano. No-null = cancelada por un cierre de
     *  emergencia (ver GymClosureService) — permite saber el motivo real de la cancelación. */
    private Long cancelledByClosureId;

    /** No-null = la reserva nació de una clase fija del socio (ver FixedClassService). Se pone en
     *  null al cancelarla por quitar el plan o la clase fija, para que no cuente como "el socio la
     *  soltó a propósito". */
    private Long fixedClassId;

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
