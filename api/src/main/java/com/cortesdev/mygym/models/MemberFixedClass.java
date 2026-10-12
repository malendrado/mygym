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

/** Clase semanal fija de un socio (un bloque: día + hora + nombre). El sistema le reserva sola
 *  cada fecha del período de su plan — ver FixedClassService. Máximo una por socio por día de la
 *  semana (se valida en el servicio, porque el día vive en gym_block). */
@Entity
@Table(name = "member_fixed_class")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberFixedClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private Long memberId;

    private Long gymBlockId;

    private Long createdByUserId;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
