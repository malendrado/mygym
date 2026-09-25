package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una TV vinculada mostrando el horario en vivo de un gym. Sesión de larga duración por diseño
 *  (una TV queda prendida meses/años) — nunca expira por tiempo fijo, solo "por abandono": ver
 *  TvScreenService.SCREEN_INACTIVITY_TTL, medido contra lastPolledAt (o createdAt si nunca hizo
 *  poll). token es el secreto opaco que la TV manda en cada poll, nunca un JWT — necesitamos
 *  poder invalidar por inactividad sin tener que reemitir nada. */
@Entity
@Table(name = "tv_screen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvScreen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private String name;

    private String token;

    private Instant createdAt;

    private Instant lastPolledAt;
}
