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

/** QR rotativo de asistencia que muestra la TV durante una clase en curso (ver migración V24).
 *  A diferencia de TvPairingCode, nace con gymId (la propia pantalla ya sabe a qué gym
 *  pertenece) y no es de un solo uso — cualquier socio con una reserva activa en ese gym puede
 *  canjearlo mientras esté vigente. */
@Entity
@Table(name = "tv_checkin_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvCheckinCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gymId;

    private String code;

    private Instant createdAt;

    private Instant expiresAt;
}
