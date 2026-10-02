package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Token de un solo uso compartido por las 3 variantes del flujo de email+contraseña (ver
 * AuthTokenPurpose) — solo se guarda tokenHash (SHA-256 del valor crudo mandado por email), nunca
 * el token en sí. Para SELF_REGISTER, appUserId queda null y los datos del alta viven en las
 * columnas pending* hasta que se confirma (PasswordAuthService.activate crea la AppUser recién ahí).
 */
@Entity
@Table(name = "auth_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appUserId;

    @Enumerated(EnumType.STRING)
    private AuthTokenPurpose purpose;

    private String tokenHash;

    private String pendingName;

    private String pendingEmail;

    private Long pendingGymId;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
