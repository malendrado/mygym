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

/** A qué paso llegó un DEMO_ADMIN en uno de los dos tours guiados ("ADMIN" o "MEMBER") —
 *  un registro por (demo_admin_id, tour), max_step solo avanza, nunca retrocede. */
@Entity
@Table(name = "demo_tour_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoTourProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long demoAdminId;

    private String tour;

    private Integer maxStep;

    private Instant reachedAt;

    @PrePersist
    void onCreate() {
        if (reachedAt == null) {
            reachedAt = Instant.now();
        }
    }
}
