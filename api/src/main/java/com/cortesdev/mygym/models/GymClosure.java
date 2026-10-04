package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gym_closure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymClosure {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private LocalDate startDate;

    private LocalDate endDate;

    /** true = cierra TODAS las clases de cada día del rango; false = solo las de gym_closure_block. */
    private boolean wholeDays;

    private String reason;

    private String createdByEmail;

    private String createdByRole;

    private Instant createdAt;

    /** Null = sigue vigente hasta endDate. No-null = se levantó antes de tiempo — ver effectiveEndDate(). */
    private Instant liftedAt;

    private int cancelledReservationsCount;

    private int affectedMembersCount;

    private int emailsSent;

    private int emailsFailed;

    /**
     * Último día que el cierre realmente cubre. Si se levantó antes de endDate, la reapertura
     * rige desde HOY en adelante (nunca retroactiva) — las reservas ya canceladas siguen
     * canceladas, solo se vuelve a poder reservar desde el día del levantamiento.
     */
    public LocalDate effectiveEndDate() {
        if (liftedAt == null) {
            return endDate;
        }
        LocalDate dayBeforeLift = liftedAt.atZone(GYM_ZONE).toLocalDate().minusDays(1);
        if (dayBeforeLift.isBefore(startDate)) {
            return startDate.minusDays(1);
        }
        return dayBeforeLift.isBefore(endDate) ? dayBeforeLift : endDate;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(effectiveEndDate());
    }
}
