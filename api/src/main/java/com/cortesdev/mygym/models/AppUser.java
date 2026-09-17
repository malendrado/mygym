package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String googleSub;

    private String name;

    /** Foto de perfil de Google (claim "picture" del ID token) — null si nunca se logueó con Google. */
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long gymId;

    private boolean active;

    /** Solo aplica a MEMBER — plan contratado y fecha del último pago (marcado a mano por un admin, ver GymService.simulatePlanPayment). */
    private Long planId;

    private Instant paidAt;

    /** Seteado solo cuando un admin agrega este socio a mano (MemberService.createMember) — null si se
     *  auto-registró vía /j/{slug}. Junto con googleSub permite distinguir "invitado pendiente" (todavía
     *  no entró) de "invitado registrado" (ya entró al menos una vez), sin depender del email para nada
     *  más que su unicidad de siempre. */
    private Instant invitedAt;

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
