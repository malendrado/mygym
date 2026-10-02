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

/** Un día del ciclo de un WorkoutPlan — solo título, sin ejercicios prescritos ("bloc de notas",
 *  no un motor de programas). orderIndex es 1-based y secuencial dentro de su workoutPlanId. */
@Entity
@Table(name = "plan_day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long workoutPlanId;

    private Integer orderIndex;

    private String title;
}
