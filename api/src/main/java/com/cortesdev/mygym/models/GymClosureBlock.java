package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una fila por cada bloque incluido en un cierre "de clases puntuales" (GymClosure.wholeDays=false). */
@Entity
@Table(name = "gym_closure_block")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymClosureBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long closureId;

    private Long gymBlockId;
}
