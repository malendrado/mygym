package com.cortesdev.mygym.models;

import jakarta.persistence.Column;
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

/**
 * Plan que mygym le vende a un gimnasio (Empieza, Crece, Escala...). No confundir con {@link GymPlan}, que es el plan que
 * un gimnasio le vende a sus socios. Alimenta la landing, el FAQ y la asignacion de plan en cada gimnasio.
 */
@Entity
@Table(name = "mygym_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MygymPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    private String clientsLabel;

    /** Precio neto mensual en CLP (sin IVA). Null = "A medida": se fija por gimnasio. */
    private Integer priceClp;

    /** Cupo de socios del plan. Null = sin tope propio (se define por gimnasio). */
    private Integer maxUsers;

    private int maxProfesores;

    private int maxAdmins;

    /** Una linea por beneficio de la tarjeta de precios. */
    @Column(columnDefinition = "text")
    private String features;

    private boolean highlighted;

    private int sortOrder;

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
