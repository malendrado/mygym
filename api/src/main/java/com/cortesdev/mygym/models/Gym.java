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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gym")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identificador opaco para la URL del panel del super-admin — el id
     * secuencial de arriba nunca se expone en una ruta (dejaba adivinar
     * cuántos gimnasios existen y filtraba el nombre del cliente en la URL).
     */
    private UUID publicId;

    private String name;

    private String slug;

    private boolean active;

    private Integer maxUsers;

    private boolean googleLoginEnabled;

    private String themeColor;

    // "DARK" (default, acento libre) o "LIGHT" (limitado a las 4 paletas curadas de
    // GymPalette.LIGHT_ALL — ver ThemeUpdateRequest/GymService.updateTheme).
    @Builder.Default
    private String themeMode = "DARK";

    @Column(columnDefinition = "text")
    private String logoSvg;

    private String tagline;

    private String description;

    private String instagramUrl;

    private String whatsappNumber;

    private int cancellationWindowHours;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
