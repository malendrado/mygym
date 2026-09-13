package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gym_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private String name;

    private String description;

    private String category;

    private Integer priceClp;

    /** Null = plan libre (reserva ilimitada en cupos disponibles). */
    private Integer monthlyClasses;

    private boolean active;

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
