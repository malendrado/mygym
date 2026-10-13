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

/** Un telefono/navegador suscrito a notificaciones push de un usuario (socio, admin o profesor). */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** URL que entrega el servicio de push del navegador (FCM, Apple, Mozilla...). Unica por dispositivo. */
    private String endpoint;

    /** Clave publica ECDH P-256 del navegador, base64url (65 bytes sin comprimir). */
    private String p256dh;

    /** Secreto de autenticacion de 16 bytes, base64url. */
    private String auth;

    private String userAgent;

    private Instant createdAt;

    private Instant lastSuccessAt;

    private int failureCount;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
