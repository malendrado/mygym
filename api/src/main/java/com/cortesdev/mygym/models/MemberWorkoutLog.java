package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 1 registro por reserva asistida (reservation.checkedInAt != null) — nunca uno suelto, ver
 *  WorkoutService. suggestedPlanDayId queda fijo para siempre (lo que el sistema calculó al
 *  crear el log); planDayId null = el socio escribió texto libre en vez de aceptar la
 *  sugerencia. Primer uso de JSONB en el proyecto (exercisesLog). */
@Entity
@Table(name = "member_workout_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberWorkoutLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reservationId;

    private Long suggestedPlanDayId;

    private Long planDayId;

    private String freeTextLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ExerciseLogEntry> exercisesLog;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
