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

/** Código corto de emparejamiento para vincular una TV (ver TvScreen) — efímero, sin gymId
 *  todavía: recién se sabe a qué gym pertenece cuando un GYM_ADMIN autenticado lo reclama desde
 *  su panel. La TV nunca tipea una URL larga, solo este código de 6 caracteres. */
@Entity
@Table(name = "tv_pairing_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvPairingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant claimedAt;

    private String screenToken;
}
