package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un socio esperando que se libere un cupo en una clase llena — ver WaitlistService.
 *  notifiedAt null = todavía no le tocó su turno de aviso; se pone en el momento en que se le
 *  manda el email (sea con ventaja de cabeza de lista, o junto al resto cuando esa ventana
 *  vence). La fila se borra sola cuando el socio reserva (WaitlistService.clearOnBooked) o si
 *  decide salir de la lista a mano — nunca queda "resuelta" con un status, simplemente deja de
 *  existir. */
@Entity
@Table(name = "class_waitlist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassWaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymBlockId;

    private Long memberId;

    private LocalDate classDate;

    private Instant createdAt;

    private Instant notifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
