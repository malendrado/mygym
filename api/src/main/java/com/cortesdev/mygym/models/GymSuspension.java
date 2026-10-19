package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Periodo en que un gimnasio estuvo suspendido por no pago (auditoria). */
@Entity
@Table(name = "gym_suspension")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private Instant startedAt;

    private Instant endedAt;

    private String reason;
}
