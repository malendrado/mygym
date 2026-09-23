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
import java.util.Locale;
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

    /** Solo aplica a MEMBER con plan de cupo limitado, cargado por el Excel de importación
     *  masiva (ver MemberImportRowService) — clases que ya venía usando en el sistema anterior
     *  del gym ese mismo mes. Se resta aparte al calcular sessionsRemaining (MemberService.
     *  toResponse) y se resetea a null apenas el plan se renueva o se le quita (GymService.
     *  simulatePlanPayment/revokePlan, FlowPaymentService) — nunca se arrastra a otro período. */
    private Integer usedSessionsAtImport;

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
        email = normalizeEmail(email);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        email = normalizeEmail(email);
    }

    /**
     * Bug real en prod: Google siempre devuelve el email en minúscula, pero un admin puede
     * tipear un email con mayúsculas al invitar — findByEmail/existsByEmail hacen match exacto,
     * así que "Nombre@mail.com" (invitado) y "nombre@mail.com" (login real de Google) generaban
     * DOS filas para la misma persona (el invitado quedaba pegado en PENDING para siempre, el
     * login real creaba una cuenta nueva sin el invitedAt). Normalizar acá garantiza que TODO lo
     * que se guarda queda en minúscula sin importar por dónde entre — pero los call sites de
     * findByEmail/existsByEmail igual tienen que normalizar el email de búsqueda (ver
     * AuthService/GymService/MemberService), esto solo cubre la escritura.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
