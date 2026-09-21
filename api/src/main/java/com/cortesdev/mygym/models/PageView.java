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

/** Una visita real (beacon disparado por JS al cargar la página) a una de las 3 superficies
 *  públicas del producto: "BROCHURE", "LANDING" o "JOIN" (la página pública de alta de un
 *  gimnasio puntual, /j/{slug}). Sin IP ni ningún dato identificable de la persona — solo qué
 *  página, cuándo, y de qué gimnasio (solo aplica a JOIN). */
@Entity
@Table(name = "page_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String page;

    private Long gymId;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
